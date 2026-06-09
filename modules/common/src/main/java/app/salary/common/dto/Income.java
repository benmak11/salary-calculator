package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import app.salary.common.constants.IncomeType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@ExcludeFromCodeCoverage
public class Income {

    @NotNull
    private IncomeType type;

    @NotNull
    @Min(0)
    private Double amount;

    @Min(0)
    private Double hoursPerWeek;

}
