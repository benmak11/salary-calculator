package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import app.salary.common.constants.StudentLoanPlan;
import jakarta.validation.constraints.Min;

@ExcludeFromCodeCoverage
public class Posttax {
    @Min(0)
    private Double fixed = 0.0;

    private StudentLoanPlan studentLoanPlan;

    public Double getFixed() { return fixed; }
    public void setFixed(Double fixed) { this.fixed = fixed; }
    public StudentLoanPlan getStudentLoanPlan() { return studentLoanPlan; }
    public void setStudentLoanPlan(StudentLoanPlan studentLoanPlan) { this.studentLoanPlan = studentLoanPlan; }
}
