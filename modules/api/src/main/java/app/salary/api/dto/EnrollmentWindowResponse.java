package app.salary.api.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

@ExcludeFromCodeCoverage
@Schema(description = "Open enrollment window status")
public class EnrollmentWindowResponse {
    @Schema(example = "OPEN", description = "OPEN | CLOSED | UPCOMING")
    private String status;
    @Schema(example = "14")
    private Integer daysRemaining;
    @Schema(example = "2024-11-01")
    private String windowStartDate;
    @Schema(example = "2024-11-30")
    private String windowEndDate;
    @Schema(example = "2024-12-01")
    private String effectiveChangeDate;

    public EnrollmentWindowResponse() {}

    public EnrollmentWindowResponse(String status, Integer daysRemaining,
                                    String windowStartDate, String windowEndDate,
                                    String effectiveChangeDate) {
        this.status = status;
        this.daysRemaining = daysRemaining;
        this.windowStartDate = windowStartDate;
        this.windowEndDate = windowEndDate;
        this.effectiveChangeDate = effectiveChangeDate;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getDaysRemaining() { return daysRemaining; }
    public void setDaysRemaining(Integer daysRemaining) { this.daysRemaining = daysRemaining; }
    public String getWindowStartDate() { return windowStartDate; }
    public void setWindowStartDate(String windowStartDate) { this.windowStartDate = windowStartDate; }
    public String getWindowEndDate() { return windowEndDate; }
    public void setWindowEndDate(String windowEndDate) { this.windowEndDate = windowEndDate; }
    public String getEffectiveChangeDate() { return effectiveChangeDate; }
    public void setEffectiveChangeDate(String effectiveChangeDate) { this.effectiveChangeDate = effectiveChangeDate; }
}
