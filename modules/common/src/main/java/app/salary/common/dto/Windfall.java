package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * A dated, one-off income event (bonus payout or RSU vest) supplied as
 * context for budget-plan generation. {@code netAmount} is already net of
 * tax — the caller sources it from {@code CalculateResponse.supplemental},
 * not a re-derived estimate.
 */
@ExcludeFromCodeCoverage
@Setter
@Getter
@Schema(description = "A dated, one-off income event (bonus/RSU vest), net of tax")
public class Windfall {
    @NotBlank
    @Schema(description = "Short label", example = "March bonus")
    private String label;

    @NotNull
    @Positive
    @Schema(description = "Net-of-tax amount", example = "7035")
    private Double netAmount;

    @NotBlank
    @Schema(description = "ISO-8601 yyyy-MM-dd", example = "2026-03-15")
    private String date;
}
