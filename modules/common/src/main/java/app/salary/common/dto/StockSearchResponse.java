package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@ExcludeFromCodeCoverage
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ticker search results, best matches first (max 10)")
public class StockSearchResponse {
    private List<StockSymbol> items;
}
