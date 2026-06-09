package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@ExcludeFromCodeCoverage
@Schema(description = "A named pre-tax or post-tax deduction line item")
public class NamedDeduction {
    @NotBlank
    @Schema(description = "Display name for this deduction", example = "Commuter Benefit")
    private String name;

    @Min(0)
    @Schema(description = "Annual deduction amount", example = "1200.0")
    private Double amount;

    public NamedDeduction() {}

    public NamedDeduction(String name, Double amount) {
        this.name = name;
        this.amount = amount;
    }

}
