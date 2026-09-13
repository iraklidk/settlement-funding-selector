package ge.kursi.settlement.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record FundingRequestDto(
        @NotNull(message = "availableSettlementBalance is required")
        @PositiveOrZero(message = "availableSettlementBalance must not be negative")
        @Digits(integer = 13, fraction = 2, message = "availableSettlementBalance must have at most 13 integer and 2 fraction digits")
        BigDecimal availableSettlementBalance,

        @NotEmpty(message = "candidateInstructions must contain at least one instruction")
        @Size(max = MAX_CANDIDATES, message = "candidateInstructions must contain at most " + MAX_CANDIDATES + " instructions")
        List<@NotNull(message = "candidateInstructions must not contain null entries") @Valid CandidateInstructionDto> candidateInstructions) {

    public static final int MAX_CANDIDATES = 1000;
}
