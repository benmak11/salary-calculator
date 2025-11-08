package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import app.salary.common.constants.IncomeType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@ExcludeFromCodeCoverage
public class Income {

    @NotNull
    private IncomeType type;

    @NotNull
    @Min(0)
    private Double amount;

    @Min(0)
    private Double hoursPerWeek;

    public IncomeType getType() {
        return type;
    }

    public void setType(IncomeType type) {
        this.type = type;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Double getHoursPerWeek() {
        return hoursPerWeek;
    }

    public void setHoursPerWeek(Double hoursPerWeek) {
        this.hoursPerWeek = hoursPerWeek;
    }
}
