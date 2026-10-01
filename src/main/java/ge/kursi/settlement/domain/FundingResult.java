package ge.kursi.settlement.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A persisted funding run, as returned to API clients */
public record FundingResult(UUID requestId,
                            BigDecimal availableSettlementBalance,
                            List<CandidateInstruction> candidateInstructions,
                            List<CandidateInstruction> selectedInstructions,
                            BigDecimal totalSettlementConsumed,
                            BigDecimal totalExpectedFee,
                            Instant createdAt) {
}
