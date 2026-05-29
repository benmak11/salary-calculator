package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import app.salary.common.constants.StudentLoanPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@ExcludeFromCodeCoverage
@Schema(description = "Post-tax deductions configuration")
public class Posttax {
    @Min(0)
    @Schema(description = "Fixed post-tax deduction amount", example = "100")
    private Double fixed = 0.0;

    @Min(0) @Max(1)
    @Schema(description = "Roth 401(k) employee contribution percentage of regular wages (0-1, US only). Post-tax federally but tagged as RETIREMENT.", example = "0.02")
    private Double roth401kPercent = 0.0;

    @Schema(description = "Student loan plan (UK only): PLAN1, PLAN2, or POSTGRAD", example = "PLAN2")
    private StudentLoanPlan studentLoanPlan;

    public Double getFixed() { return fixed; }
    public void setFixed(Double fixed) { this.fixed = fixed; }
    public Double getRoth401kPercent() { return roth401kPercent; }
    public void setRoth401kPercent(Double roth401kPercent) { this.roth401kPercent = roth401kPercent; }
    public StudentLoanPlan getStudentLoanPlan() { return studentLoanPlan; }
    public void setStudentLoanPlan(StudentLoanPlan studentLoanPlan) { this.studentLoanPlan = studentLoanPlan; }
}
