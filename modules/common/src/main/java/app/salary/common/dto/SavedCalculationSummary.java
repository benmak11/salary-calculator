package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@ExcludeFromCodeCoverage
@Schema(description = "Denormalized row data for the History list view")
public class SavedCalculationSummary {
    @Schema(description = "Stable session id")
    private String id;

    @Schema(description = "Save timestamp in ISO-8601 UTC", example = "2026-06-09T18:42:00Z")
    private String savedAt;

    @Schema(description = "ISO country code", example = "US")
    private String country;

    @Schema(description = "Tax year", example = "2025")
    private Integer taxYear;

    @Schema(description = "Pay cadence as supplied in the original request", example = "BIWEEKLY")
    private String cadence;

    @Schema(description = "State code when country=US, else null", example = "CA")
    private String stateCode;

    @Schema(description = "Human-readable state name when country=US, else null", example = "California")
    private String stateName;

    @Schema(description = "ISO currency", example = "USD")
    private String currency;

    @Schema(description = "Take-home pay for one pay period")
    private Double takeHomePerPeriod;

    @Schema(description = "Total taxes for one pay period")
    private Double taxesPerPeriod;

    @Schema(description = "Total benefits + retirement for one pay period")
    private Double benefitsPerPeriod;

    @Schema(description = "Gross pay for one pay period")
    private Double grossPerPeriod;

    @Schema(description = "Take-home as a percentage of gross (0-100)")
    private Double takeHomePct;

    public SavedCalculationSummary() {}

}
