package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

@ExcludeFromCodeCoverage
@Schema(description = "Individual earnings, tax, or deduction line item")
public class LineItem {
    @Schema(description = "Name of the line item (e.g., 'Federal Income Tax', 'Medicare')", example = "Federal Income Tax")
    private String name;

    @Schema(description = "Amount for this line item", example = "13841.0")
    private Double amount;

    @Schema(description = "Category for client-side grouping (donut wedges / right-rail sections). Null on legacy emitters until Phase 2.", example = "TAX_FEDERAL")
    private LineItemCategory category;

    public LineItem() {}

    public LineItem(String name, Double amount) {
        this.name = name;
        this.amount = amount;
    }

    public LineItem(String name, Double amount, LineItemCategory category) {
        this.name = name;
        this.amount = amount;
        this.category = category;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public LineItemCategory getCategory() { return category; }
    public void setCategory(LineItemCategory category) { this.category = category; }
}
