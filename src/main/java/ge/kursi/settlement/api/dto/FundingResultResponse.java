package ge.kursi.settlement.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FundingResultResponse(UUID requestId,
                                    BigDecimal availableSettlementBalance,
                                    List<CandidateInstructionDto> candidateInstructions,
                                    List<CandidateInstructionDto> selectedInstructions,
                                    BigDecimal totalSettlementConsumed,
                                    BigDecimal totalExpectedFee,
                                    Instant createdAt) {
}
