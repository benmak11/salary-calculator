package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
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

}
