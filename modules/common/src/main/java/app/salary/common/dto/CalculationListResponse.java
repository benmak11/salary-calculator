package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@ExcludeFromCodeCoverage
@Schema(description = "Paginated list of saved calculations, newest first")
public class CalculationListResponse {
    @Schema(description = "Page of summaries")
    private List<SavedCalculationSummary> items;

    @Schema(description = "Opaque cursor for the next page; null when no further results")
    private String nextCursor;

    public CalculationListResponse() {}

    public CalculationListResponse(List<SavedCalculationSummary> items, String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }

}
