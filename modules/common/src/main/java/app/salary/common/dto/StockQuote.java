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
@Schema(description = "Current share price for a symbol")
public class StockQuote {
    @Schema(description = "Ticker symbol", example = "AAPL")
    private String symbol;

    @Schema(description = "Current price per share (USD)", example = "232.14")
    private Double price;

    @Schema(description = "When the price was observed (ISO-8601 instant)", example = "2026-07-16T14:30:00Z")
    private String asOf;
}
