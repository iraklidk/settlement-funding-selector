package ge.kursi.settlement.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ge.kursi.settlement.persistence.FundingRequestRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Full-stack API tests: HTTP -> controller -> service -> algorithm -> JPA -> H2 (PostgreSQL mode). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettlementControllerIT {

    private static final String ASSIGNMENT_EXAMPLE = """
            {
              "availableSettlementBalance": 20000,
              "candidateInstructions": [
                { "instructionReference": "INS-2001", "instructionAmount": 7000, "expectedFee": 150 },
                { "instructionReference": "INS-2002", "instructionAmount": 9000, "expectedFee": 210 },
                { "instructionReference": "INS-2003", "instructionAmount": 4000, "expectedFee": 90 },
                { "instructionReference": "INS-2004", "instructionAmount": 6000, "expectedFee": 130 }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FundingRequestRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private MvcResult fund(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/settlement/fund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Nested
    class FundEndpoint {

        @Test
        void returns201WithOptimalSelectionForAssignmentExample() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/settlement/fund")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ASSIGNMENT_EXAMPLE))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString("/api/v1/settlement/")))
                    .andExpect(jsonPath("$.requestId").isNotEmpty())
                    .andExpect(jsonPath("$.createdAt").isNotEmpty())
                    .andExpect(jsonPath("$.availableSettlementBalance").value(20000.00))
                    .andExpect(jsonPath("$.totalSettlementConsumed").value(20000.00))
                    .andExpect(jsonPath("$.totalExpectedFee").value(450.00))
                    .andExpect(jsonPath("$.candidateInstructions", hasSize(4)))
                    .andExpect(jsonPath("$.selectedInstructions", hasSize(3)))
                    .andExpect(jsonPath("$.selectedInstructions[0].instructionReference").value("INS-2001"))
                    .andExpect(jsonPath("$.selectedInstructions[0].instructionAmount").value(7000.00))
                    .andExpect(jsonPath("$.selectedInstructions[0].expectedFee").value(150.00))
                    .andExpect(jsonPath("$.selectedInstructions[1].instructionReference").value("INS-2002"))
                    .andExpect(jsonPath("$.selectedInstructions[2].instructionReference").value("INS-2003"))
                    .andReturn();

            UUID requestId = UUID.fromString(json(result).get("requestId").asText());
            assertThat(repository.findWithInstructionsById(requestId)).isPresent();
            assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/settlement/" + requestId);
        }

        @Test
        void returns201WithEmptySelectionWhenNothingFits() throws Exception {
            String body = """
                    {
                      "availableSettlementBalance": 100,
                      "candidateInstructions": [
                        { "instructionReference": "BIG-1", "instructionAmount": 100.01, "expectedFee": 5 },
                        { "instructionReference": "BIG-2", "instructionAmount": 250, "expectedFee": 9 }
                      ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/settlement/fund").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.selectedInstructions", hasSize(0)))
                    .andExpect(jsonPath("$.totalSettlementConsumed").value(0))
                    .andExpect(jsonPath("$.totalExpectedFee").value(0))
                    .andExpect(jsonPath("$.candidateInstructions", hasSize(2)));
        }

        @Test
        void returns400ForMissingAndInvalidFields() throws Exception {
            String body = """
                    {
                      "candidateInstructions": [
                        { "instructionReference": "  ", "instructionAmount": -5, "expectedFee": 1.005 },
                        { "instructionReference": "OK", "expectedFee": 1 }
                      ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/settlement/fund").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Request validation failed"))
                    .andExpect(jsonPath("$.path").value("/api/v1/settlement/fund"))
                    .andExpect(jsonPath("$.details", hasItem(containsString("availableSettlementBalance is required"))))
                    .andExpect(jsonPath("$.details", hasItem(containsString("instructionReference must not be blank"))))
                    .andExpect(jsonPath("$.details", hasItem(containsString("instructionAmount must be greater than 0"))))
                    .andExpect(jsonPath("$.details", hasItem(containsString("expectedFee must have at most"))))
                    .andExpect(jsonPath("$.details", hasItem(containsString("instructionAmount is required"))));
        }

        @Test
        void returns400ForEmptyCandidateList() throws Exception {
            String body = """
                    { "availableSettlementBalance": 100, "candidateInstructions": [] }
                    """;

            mockMvc.perform(post("/api/v1/settlement/fund").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasItem(containsString("at least one instruction"))));
        }

        @Test
        void returns400ForNegativeBalance() throws Exception {
            String body = """
                    {
                      "availableSettlementBalance": -1,
                      "candidateInstructions": [
                        { "instructionReference": "A", "instructionAmount": 1, "expectedFee": 1 }
                      ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/settlement/fund").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasItem(containsString("availableSettlementBalance must not be negative"))));
        }

        @Test
        void returns400ForDuplicateReferences() throws Exception {
            String body = """
                    {
                      "availableSettlementBalance": 100,
                      "candidateInstructions": [
                        { "instructionReference": "INS-1", "instructionAmount": 10, "expectedFee": 1 },
                        { "instructionReference": "INS-1", "instructionAmount": 20, "expectedFee": 2 }
                      ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/settlement/fund").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Duplicate instructionReference 'INS-1'")));
            assertThat(repository.count()).isZero();
        }

        @Test
        void returns400ForMalformedJson() throws Exception {
            mockMvc.perform(post("/api/v1/settlement/fund")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"availableSettlementBalance\": \"lots\", "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Malformed request body")));
        }

        @Test
        void returns400ForNonNumericAmount() throws Exception {
            String body = """
                    {
                      "availableSettlementBalance": "twenty thousand",
                      "candidateInstructions": [
                        { "instructionReference": "A", "instructionAmount": 1, "expectedFee": 1 }
                      ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/settlement/fund").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Malformed request body")));
        }

        @Test
        void returns415ForNonJsonContentType() throws Exception {
            mockMvc.perform(post("/api/v1/settlement/fund").contentType(MediaType.TEXT_PLAIN).content("x"))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Nested
    class GetByIdEndpoint {

        @Test
        void returnsPersistedResultIdenticalToFundResponse() throws Exception {
            MvcResult created = fund(ASSIGNMENT_EXAMPLE);
            String requestId = json(created).get("requestId").asText();

            MvcResult fetched = mockMvc.perform(get("/api/v1/settlement/{id}", requestId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestId").value(requestId))
                    .andExpect(jsonPath("$.totalExpectedFee").value(450.00))
                    .andExpect(jsonPath("$.selectedInstructions", hasSize(3)))
                    .andReturn();

            assertThat(json(fetched)).isEqualTo(json(created));
        }

        @Test
        void returns404ForUnknownId() throws Exception {
            UUID unknown = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/settlement/{id}", unknown))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value(containsString(unknown.toString())));
        }

        @Test
        void returns400ForMalformedId() throws Exception {
            mockMvc.perform(get("/api/v1/settlement/not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("requestId")))
                    .andExpect(jsonPath("$.message").value(containsString("UUID")));
        }
    }

    @Nested
    class ListEndpoint {

        @Test
        void returnsEmptyPageWhenNoRuns() throws Exception {
            mockMvc.perform(get("/api/v1/settlement"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.totalPages").value(0))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        void returnsRunsNewestFirstWithPagination() throws Exception {
            String first = json(fund(ASSIGNMENT_EXAMPLE)).get("requestId").asText();
            String second = json(fund(ASSIGNMENT_EXAMPLE)).get("requestId").asText();
            String third = json(fund(ASSIGNMENT_EXAMPLE)).get("requestId").asText();

            mockMvc.perform(get("/api/v1/settlement").param("page", "0").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].requestId").value(third))
                    .andExpect(jsonPath("$.content[1].requestId").value(second))
                    .andExpect(jsonPath("$.content[0].candidateCount").value(4))
                    .andExpect(jsonPath("$.content[0].selectedCount").value(3))
                    .andExpect(jsonPath("$.content[0].totalExpectedFee").value(450.00))
                    .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.hasNext").value(true));

            mockMvc.perform(get("/api/v1/settlement").param("page", "1").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].requestId").value(first))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        void returns400ForInvalidPagingParameters() throws Exception {
            mockMvc.perform(get("/api/v1/settlement").param("page", "-1").param("size", "500"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasItem(containsString("page must be 0 or greater"))))
                    .andExpect(jsonPath("$.details", hasItem(containsString("size must be at most 100"))));

            mockMvc.perform(get("/api/v1/settlement").param("size", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("'size'")));
        }
    }
}
