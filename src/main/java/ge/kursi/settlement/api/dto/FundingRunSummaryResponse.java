package ge.kursi.settlement.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FundingRunSummaryResponse(UUID requestId,
                                        BigDecimal availableSettlementBalance,
                                        BigDecimal totalSettlementConsumed,
                                        BigDecimal totalExpectedFee,
                                        int candidateCount,
                                        int selectedCount,
                                        Instant createdAt) {
}
