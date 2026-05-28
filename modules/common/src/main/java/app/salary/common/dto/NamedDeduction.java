package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
}
