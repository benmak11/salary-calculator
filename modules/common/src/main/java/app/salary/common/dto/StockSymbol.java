package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@ExcludeFromCodeCoverage
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A ticker search result")
public class StockSymbol {
    @Schema(description = "Ticker symbol", example = "AAPL")
    private String symbol;

    @Schema(description = "Company name", example = "Apple Inc.")
    private String name;
}
