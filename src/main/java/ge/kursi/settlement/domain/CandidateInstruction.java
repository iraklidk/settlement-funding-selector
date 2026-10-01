package ge.kursi.settlement.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** A settlement instruction that may be funded from the settlement account */
public record CandidateInstruction(String instructionReference, BigDecimal instructionAmount, BigDecimal expectedFee) {

    public CandidateInstruction {
        Objects.requireNonNull(instructionReference, "instructionReference");
        Objects.requireNonNull(instructionAmount, "instructionAmount");
        Objects.requireNonNull(expectedFee, "expectedFee");
    }
}
