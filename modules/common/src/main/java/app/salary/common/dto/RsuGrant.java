package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * An RSU grant saved by a signed-in user. The server stores grants for cross-device
 * sync only — vest-schedule math and the vesting-value roll-up happen on the client,
 * which sends the resulting {@code earnings.rsuVesting} figure on /v1/calculate.
 */
@ExcludeFromCodeCoverage
@Setter
@Getter
@Schema(description = "RSU grant: company + share count + grant date + vesting schedule")
public class RsuGrant {
    @Schema(description = "Server-assigned grant id; ignored on create", example = "g_a1b2c3d4")
    private String id;

    @Schema(description = "Ticker symbol (absent for private companies entered manually)", example = "AAPL")
    private String ticker;

    @Schema(description = "Company display name", example = "Apple Inc.")
    private String company;

    @Schema(description = "True when the price was entered manually rather than from a quote", example = "false")
    private Boolean manualPrice = false;

    @NotNull
    @Positive
    @Schema(description = "Total shares in the grant", example = "400")
    private Double sharesTotal;

    @NotNull
    @Positive
    @Schema(description = "Price per share used for valuation", example = "232.14")
    private Double pricePerShare;

    @NotBlank
    @Schema(description = "Grant date (ISO-8601 yyyy-MM-dd)", example = "2025-03-15")
    private String grantDate;

    @NotNull
    @Valid
    @Schema(description = "Vesting schedule terms")
    private VestingSchedule schedule;

    @ExcludeFromCodeCoverage
    @Setter
    @Getter
    @Schema(description = "Vesting schedule: total duration, cliff, and vest frequency in months")
    public static class VestingSchedule {
        @Schema(description = "Client preset id (annual4, monthly1cliff, quarterly1cliff, custom)",
                example = "monthly1cliff")
        private String presetId;

        @NotNull
        @Min(1)
        @Schema(description = "Total vesting duration in months", example = "48")
        private Integer totalMonths;

        @NotNull
        @Min(0)
        @Schema(description = "Cliff length in months (0 = no cliff)", example = "12")
        private Integer cliffMonths;

        @NotNull
        @Min(1)
        @Schema(description = "Months between vests after the cliff", example = "1")
        private Integer freqMonths;
    }
}
