package ge.kursi.settlement.api.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CandidateInstructionDto(
        @NotBlank(message = "instructionReference must not be blank")
        @Size(max = 128, message = "instructionReference must be at most 128 characters")
        String instructionReference,

        @NotNull(message = "instructionAmount is required")
        @Positive(message = "instructionAmount must be greater than 0")
        @Digits(integer = 13, fraction = 2, message = "instructionAmount must have at most 13 integer and 2 fraction digits")
        BigDecimal instructionAmount,

        @NotNull(message = "expectedFee is required")
        @PositiveOrZero(message = "expectedFee must not be negative")
        @Digits(integer = 13, fraction = 2, message = "expectedFee must have at most 13 integer and 2 fraction digits")
        BigDecimal expectedFee) {
}
