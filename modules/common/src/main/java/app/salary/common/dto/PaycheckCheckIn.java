package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

/**
 * One payday confirmed against its prediction. <b>Manual entry, never bank-connected</b> —
 * a Plaid integration is a different regulatory and app-review tier, and staying manual
 * keeps the product out of it entirely.
 *
 * <p>Check-in itself is <b>free forever</b> and is never gated. Pro sells the interpretation
 * of this data (YTD totals, contribution limits, drift alerts), not the act of recording it:
 * gating the recording would convert the growth loop into a conversion event and kill both.
 *
 * <p>The optional breakdown fields exist because the YTD tracker needs gross, tax and
 * retirement contributions to project against IRS limits. They are optional so a user can
 * confirm a payday in one tap with only the net, which is the whole point of the interaction.
 */
@ExcludeFromCodeCoverage
@Setter
@Getter
@Schema(description = "A payday's actual figures, confirmed by the user against the prediction")
public class PaycheckCheckIn {

    @Schema(description = "Server-assigned id; ignored on create", example = "01JKM8YQ3C5P0RXWZV6T2ND4HB")
    private String id;

    @NotBlank
    @Schema(description = "The payday being confirmed, ISO yyyy-MM-dd. One check-in per date.",
            example = "2026-08-14")
    private String payDate;

    @NotNull
    @PositiveOrZero
    @Schema(description = "Net pay that actually landed", example = "2847.13")
    private Double actualNet;

    @PositiveOrZero
    @Schema(description = "Net pay that was predicted, carried so drift can be computed without "
            + "re-running the calculation against rules that may since have changed",
            example = "2920.00")
    private Double expectedNet;

    @Schema(description = "Calculation this payday was predicted from, when there was one",
            example = "8f14e45fceea167a")
    private String calculationId;

    // ── Optional breakdown, for the YTD tracker ──────────────────────────

    @PositiveOrZero
    @Schema(description = "Gross pay for the period", example = "4000.00")
    private Double grossPay;

    @PositiveOrZero
    @Schema(description = "Federal income tax withheld", example = "506.54")
    private Double federalTax;

    @PositiveOrZero
    @Schema(description = "Employee 401(k) contribution for the period", example = "240.00")
    private Double retirement401k;

    @PositiveOrZero
    @Schema(description = "Employee HSA contribution for the period", example = "150.00")
    private Double hsaContribution;

    @Schema(description = "When the check-in was recorded, ISO-8601", example = "2026-08-14T09:12:04Z")
    private String recordedAt;
}
