# Correspondent Settlement Funding Selector

Backend service for Kursi.ge's treasury desk. Given the settlement account's available balance
and a list of candidate correspondent-bank instructions, it selects the combination that
**maximises total expected fee without exceeding the balance**, persists the request and the
decision for audit, and exposes the audit trail over REST.

Selecting instructions is a **0/1 knapsack problem**; the service solves it exactly (no greedy
approximation) — see [Algorithm](#algorithm).

## Tech stack

| Concern             | Choice                                                      |
|---------------------|-------------------------------------------------------------|
| Language / runtime  | Java 17                                                     |
| Framework           | Spring Boot 3.5 (Web MVC, Data JPA, Validation, Actuator)   |
| Database            | PostgreSQL 16 (Docker Compose), Flyway migrations           |
| Build               | Gradle 8.14 (wrapper included, no local install needed)     |
| Tests               | JUnit 5, Mockito, AssertJ, MockMvc, H2 in PostgreSQL mode   |

## Project layout

```
ge.kursi.settlement
├── api           SettlementController, request/response DTOs, GlobalExceptionHandler
├── service       SettlementFundingService – orchestration, semantic validation, transactions
├── algorithm     FundingSelector + KnapsackSolver implementations (DP and branch-and-bound)
├── persistence   JPA entities + FundingRequestRepository
├── domain        Framework-free records shared between layers
└── config        FundingProperties, Clock bean
src/main/resources/db/migration   Flyway SQL migrations
docker-compose.yml                PostgreSQL for local development
```

Request flow: `Controller` (HTTP + Bean Validation) → `Service` (normalisation, duplicate check,
transaction) → `FundingSelector` (knapsack) → `Repository` (JPA) → response mapped back to DTOs.

## Build and run

### Prerequisites

* JDK 17+
* Docker (for the database only — the application itself runs as a plain JAR)

### 1. Start the database

```bash
docker compose up -d
```

This starts PostgreSQL 16 on `localhost:5432` with database/user/password `settlement`
and a health check. Data lives in the `settlement-pgdata` volume (`docker compose down -v` wipes it).

### 2. Build

```bash
./gradlew clean build          # compiles, runs all tests, produces the JAR
```

On Windows use `gradlew.bat`. The tests need no external services.

### 3. Run

```bash
java -jar build/libs/settlement-funding-selector.jar
```

Flyway applies the schema on startup; the API is available at `http://localhost:8080`,
health at `http://localhost:8080/actuator/health`.

Alternatively during development: `./gradlew bootRun`.

### Configuration

All settings have defaults matching `docker-compose.yml`; override with environment variables:

| Variable      | Default                                     |
|---------------|---------------------------------------------|
| `DB_URL`      | `jdbc:postgresql://localhost:5432/settlement` |
| `DB_USERNAME` | `settlement`                                |
| `DB_PASSWORD` | `settlement`                                |
| `SERVER_PORT` | `8080`                                      |

`settlement.funding.dp-max-cells` (default 50,000,000) caps the size of the dynamic-programming
table before the solver switches to branch-and-bound (see [Algorithm](#algorithm)).

## API

Base path: `/api/v1/settlement`. All bodies are JSON. Money values are decimals with at most
2 fraction digits and are echoed back with scale 2 (`7000` → `7000.00`).

### 1. `POST /api/v1/settlement/fund` — run a funding selection

Runs the selection, persists request + result, returns **201 Created** with a `Location` header.

```bash
curl -i -X POST http://localhost:8080/api/v1/settlement/fund \
  -H "Content-Type: application/json" \
  -d '{
    "availableSettlementBalance": 20000,
    "candidateInstructions": [
      { "instructionReference": "INS-2001", "instructionAmount": 7000, "expectedFee": 150 },
      { "instructionReference": "INS-2002", "instructionAmount": 9000, "expectedFee": 210 },
      { "instructionReference": "INS-2003", "instructionAmount": 4000, "expectedFee": 90 },
      { "instructionReference": "INS-2004", "instructionAmount": 6000, "expectedFee": 130 }
    ]
  }'
```

```http
HTTP/1.1 201
Location: http://localhost:8080/api/v1/settlement/6c90aa2b-d2d2-4410-a581-d0cc360b7df0
Content-Type: application/json

{
  "requestId": "6c90aa2b-d2d2-4410-a581-d0cc360b7df0",
  "availableSettlementBalance": 20000.00,
  "candidateInstructions": [
    { "instructionReference": "INS-2001", "instructionAmount": 7000.00, "expectedFee": 150.00 },
    { "instructionReference": "INS-2002", "instructionAmount": 9000.00, "expectedFee": 210.00 },
    { "instructionReference": "INS-2003", "instructionAmount": 4000.00, "expectedFee": 90.00 },
    { "instructionReference": "INS-2004", "instructionAmount": 6000.00, "expectedFee": 130.00 }
  ],
  "selectedInstructions": [
    { "instructionReference": "INS-2001", "instructionAmount": 7000.00, "expectedFee": 150.00 },
    { "instructionReference": "INS-2002", "instructionAmount": 9000.00, "expectedFee": 210.00 },
    { "instructionReference": "INS-2003", "instructionAmount": 4000.00, "expectedFee": 90.00 }
  ],
  "totalSettlementConsumed": 20000.00,
  "totalExpectedFee": 450.00,
  "createdAt": "2026-09-10T08:46:34.391509Z"
}
```

In addition to the fields from the assignment, the response includes `availableSettlementBalance`
and the full `candidateInstructions` list so an auditor can see the complete input that led to
the decision, not only the winners.

When nothing fits the balance the run is still recorded and returns **201** with an empty selection:

```bash
curl -s -X POST http://localhost:8080/api/v1/settlement/fund \
  -H "Content-Type: application/json" \
  -d '{ "availableSettlementBalance": 100,
        "candidateInstructions": [ { "instructionReference": "BIG-1", "instructionAmount": 250.50, "expectedFee": 9 } ] }'
```

```json
{
  "requestId": "046312f0-0109-47f2-b0f1-b56084837bf8",
  "availableSettlementBalance": 100.00,
  "candidateInstructions": [ { "instructionReference": "BIG-1", "instructionAmount": 250.50, "expectedFee": 9.00 } ],
  "selectedInstructions": [],
  "totalSettlementConsumed": 0.00,
  "totalExpectedFee": 0.00,
  "createdAt": "2026-09-10T08:46:35.756208Z"
}
```

Invalid input returns **400** with a descriptive message and one entry per violation:

```bash
curl -s -X POST http://localhost:8080/api/v1/settlement/fund \
  -H "Content-Type: application/json" \
  -d '{ "availableSettlementBalance": -5,
        "candidateInstructions": [ { "instructionReference": "", "instructionAmount": 0, "expectedFee": 1.234 } ] }'
```

```json
{
  "timestamp": "2026-09-10T08:46:35.666851800Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/v1/settlement/fund",
  "details": [
    "availableSettlementBalance: availableSettlementBalance must not be negative",
    "candidateInstructions[0].expectedFee: expectedFee must have at most 13 integer and 2 fraction digits",
    "candidateInstructions[0].instructionAmount: instructionAmount must be greater than 0",
    "candidateInstructions[0].instructionReference: instructionReference must not be blank"
  ]
}
```

Validation rules:

| Field                        | Rule                                                            |
|------------------------------|-----------------------------------------------------------------|
| `availableSettlementBalance` | required, ≥ 0, ≤ 13 integer + 2 fraction digits                 |
| `candidateInstructions`      | required, 1 – 1000 entries, no `null` entries                   |
| `instructionReference`       | required, non-blank, ≤ 128 chars, **unique within the request** |
| `instructionAmount`          | required, > 0, ≤ 13 integer + 2 fraction digits                 |
| `expectedFee`                | required, ≥ 0, ≤ 13 integer + 2 fraction digits                 |

Malformed JSON, wrong types (`"availableSettlementBalance": "lots"`) and duplicate references
also produce 400; a non-JSON `Content-Type` produces 415.

### 2. `GET /api/v1/settlement/{requestId}` — fetch a persisted run

```bash
curl -s http://localhost:8080/api/v1/settlement/6c90aa2b-d2d2-4410-a581-d0cc360b7df0
```

Returns **200** with exactly the same body as the original `POST` response.

```bash
curl -s http://localhost:8080/api/v1/settlement/00000000-0000-0000-0000-000000000000
```

```json
{
  "timestamp": "2026-09-10T08:46:35.462882300Z",
  "status": 404,
  "error": "Not Found",
  "message": "Funding request not found: 00000000-0000-0000-0000-000000000000",
  "path": "/api/v1/settlement/00000000-0000-0000-0000-000000000000"
}
```

A syntactically invalid id (`/api/v1/settlement/abc`) returns 400:
`"Parameter 'requestId' has invalid value 'abc': expected a valid UUID"`.

### 3. `GET /api/v1/settlement` — paginated audit trail, newest first

Query parameters: `page` (0-based, default 0) and `size` (1–100, default 20).

```bash
curl -s "http://localhost:8080/api/v1/settlement?page=0&size=10"
```

```json
{
  "content": [
    {
      "requestId": "046312f0-0109-47f2-b0f1-b56084837bf8",
      "availableSettlementBalance": 100.00,
      "totalSettlementConsumed": 0.00,
      "totalExpectedFee": 0.00,
      "candidateCount": 1,
      "selectedCount": 0,
      "createdAt": "2026-09-10T08:46:35.756208Z"
    },
    {
      "requestId": "6c90aa2b-d2d2-4410-a581-d0cc360b7df0",
      "availableSettlementBalance": 20000.00,
      "totalSettlementConsumed": 20000.00,
      "totalExpectedFee": 450.00,
      "candidateCount": 4,
      "selectedCount": 3,
      "createdAt": "2026-09-10T08:46:34.391509Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 2,
  "totalPages": 1,
  "hasNext": false
}
```

The listing returns per-run summaries (one row each, no instruction lists) so a large audit trail
stays cheap to page through; the detail endpoint returns the full instruction set.
`page=-1` or `size=1000` returns 400 with the offending parameters in `details`.

## Algorithm

`FundingSelector` turns the request into a 0/1 knapsack instance and solves it **exactly**:

1. **Exact money arithmetic.** All amounts are converted to minor units (`long`, scale 2), so
   there is no floating-point drift (`0.10 + 0.20` is exactly `0.30`).
2. **Capacity compression.** Instructions larger than the balance are dropped up front, then every
   amount and the balance are divided by their greatest common divisor. Real-world settlement
   amounts are usually round, so this shrinks the capacity dramatically (the assignment example
   goes from 2,000,000 minor units to 20).
3. **Solver choice.**
   * `DynamicProgrammingKnapsackSolver` — classic `O(n × capacity)` table with bit-set
     reconstruction. Used while `n × capacity ≤ settlement.funding.dp-max-cells`.
   * `BranchAndBoundKnapsackSolver` — depth-first search ordered by fee density with a fractional
     knapsack upper bound. Its cost does not depend on the capacity magnitude, so it handles
     large balances with cent-level amounts where a DP table would not fit in memory.

   Both implement the same `KnapsackSolver` contract and are cross-checked against each other
   and against brute force in the tests.
4. **Deterministic tie-breaking.** If several selections yield the same maximal fee, the one that
   consumes the least balance wins (leaves the most liquidity); remaining ties prefer instructions
   earlier in the input. Selected instructions are returned in input order.

Instructions are never partially funded, and a run whose best selection is empty is still
persisted (constraint 1 of the assignment).

## Database schema

Two tables, created by `V1__create_funding_tables.sql`:

```
funding_request                       funding_instruction
───────────────────────────────       ─────────────────────────────────────
id (UUID, PK)                    ◄─┐  id (BIGINT identity, PK)
available_settlement_balance       └─ request_id (UUID, FK, ON DELETE CASCADE)
total_settlement_consumed             input_order
total_expected_fee                    instruction_reference
candidate_count                       instruction_amount
selected_count                        expected_fee
created_at (TIMESTAMPTZ)              selected (BOOLEAN)
```

Design notes:

* **`funding_request`** is the audit header: one row per `POST /fund`, with the input balance, the
  computed totals and denormalised counts so the listing endpoint never has to join or aggregate
  the child table.
* **`funding_instruction`** stores *every* candidate, not only the selected ones, with a `selected`
  flag and the original `input_order`. An auditor can therefore reconstruct exactly what the desk
  saw and why a given instruction was left out. Rows are immutable once written.
* Money columns are `NUMERIC(19,2)`; the application validates 13+2 digits so sums of up to 1000
  instructions cannot overflow the column or a Java `long` in minor units.
* `CHECK` constraints mirror the API validation (`instruction_amount > 0`, `expected_fee >= 0`,
  `total_settlement_consumed <= available_settlement_balance`, `selected_count <= candidate_count`)
  so bad data cannot enter through any other path either.
* `UUID` primary key for `funding_request` lets the service assign the id before the insert and
  return it in the same transaction, and does not leak run volume the way a sequence would.

Index choices:

| Index                                                        | Serves                                                                                           |
|--------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| `pk_funding_request (id)`                                    | `GET /{requestId}`                                                                               |
| `idx_funding_request_created_at (created_at DESC, id DESC)`  | `GET /` audit listing — matches the exact `ORDER BY`, so paging is an index range scan with no sort; `id` is the tie-breaker that keeps pagination stable when two runs share a timestamp |
| `uq_funding_instruction_reference (request_id, instruction_reference)` | Enforces "references unique within a run" at the database level                                  |
| `idx_funding_instruction_request (request_id, input_order)`  | Loading a run's instructions in input order (`findWithInstructionsById` entity graph) and the FK's cascade delete |

No index on `selected`, amounts or fees: the service never queries by them, and each extra index
would slow every insert for no read benefit.

## Tests

```bash
./gradlew test
```

* **Unit** — `KnapsackSolverContractTest` runs every case against both solvers, including a
  300-instance randomised comparison with brute force; `FundingSelectorTest` covers money
  scaling, gcd compression, solver switching and the DP/branch-and-bound agreement;
  `SettlementFundingServiceTest` covers normalisation, duplicate rejection and entity mapping
  with mocked collaborators.
* **Integration** — `FundingRequestRepositoryTest` (`@DataJpaTest`) and `SettlementControllerIT`
  (`@SpringBootTest` + MockMvc) run the real Flyway migration against in-memory H2 in PostgreSQL
  compatibility mode with `ddl-auto=validate`, so the JPA mapping is checked against the migrated
  schema and the full HTTP → database path is exercised for all three endpoints, including every
  400/404/415 case.

H2 was chosen over Testcontainers so the suite runs anywhere without Docker; the same migration
runs unchanged on PostgreSQL 16 at application startup.
