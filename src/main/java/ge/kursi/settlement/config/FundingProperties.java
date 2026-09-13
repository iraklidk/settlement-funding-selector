package ge.kursi.settlement.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tunables for the funding selection algorithm.
 *
 * @param dpMaxCells upper bound on {@code candidates x capacity} for which the dynamic-programming
 *                   solver is used; above it the branch-and-bound solver takes over.
 */
@Validated
@ConfigurationProperties(prefix = "settlement.funding")
public record FundingProperties(@DefaultValue("50000000") @Positive long dpMaxCells) {
}
