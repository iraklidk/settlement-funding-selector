package ge.kursi.settlement.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Lightweight view of a funding run for the audit-trail listing */
public record FundingRunSummary(UUID requestId,
                                BigDecimal availableSettlementBalance,
                                BigDecimal totalSettlementConsumed,
                                BigDecimal totalExpectedFee,
                                int candidateCount,
                                int selectedCount,
                                Instant createdAt) {
}
