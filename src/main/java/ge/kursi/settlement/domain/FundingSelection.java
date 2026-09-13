package ge.kursi.settlement.domain;

import java.math.BigDecimal;
import java.util.List;

/** Outcome of the selection algorithm: which candidates to fund and the resulting totals. */
public record FundingSelection(List<CandidateInstruction> selectedInstructions,
                               BigDecimal totalSettlementConsumed,
                               BigDecimal totalExpectedFee) {

    public static FundingSelection of(List<CandidateInstruction> selected) {
        BigDecimal consumed = selected.stream()
                .map(CandidateInstruction::instructionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fee = selected.stream()
                .map(CandidateInstruction::expectedFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FundingSelection(List.copyOf(selected), consumed, fee);
    }
}
