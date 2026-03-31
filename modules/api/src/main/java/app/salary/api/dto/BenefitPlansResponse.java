package app.salary.api.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@ExcludeFromCodeCoverage
@Schema(description = "Current enrolled benefit plans for the employee")
public class BenefitPlansResponse {
    @Schema(example = "2024")
    private Integer planYear;
    @Schema(example = "ACTIVE")
    private String enrollmentStatus;
    private List<BenefitPlan> health;

    public BenefitPlansResponse() {}

    public BenefitPlansResponse(Integer planYear, String enrollmentStatus, List<BenefitPlan> health) {
        this.planYear = planYear;
        this.enrollmentStatus = enrollmentStatus;
        this.health = health;
    }

    public Integer getPlanYear() { return planYear; }
    public void setPlanYear(Integer planYear) { this.planYear = planYear; }
    public String getEnrollmentStatus() { return enrollmentStatus; }
    public void setEnrollmentStatus(String enrollmentStatus) { this.enrollmentStatus = enrollmentStatus; }
    public List<BenefitPlan> getHealth() { return health; }
    public void setHealth(List<BenefitPlan> health) { this.health = health; }
}
