package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@ExcludeFromCodeCoverage
@Schema(description = "Full saved-session payload: the original inputs and the resulting calculation")
public class SavedCalculationDetail {
    @Schema(description = "Denormalized row data, same as the list-response entries")
    private SavedCalculationSummary summary;

    @Schema(description = "The original request submitted to /v1/calculate")
    private CalculateRequest request;

    @Schema(description = "The /v1/calculate response that was returned at save time")
    private CalculateResponse response;

    public SavedCalculationDetail() {}

    public SavedCalculationDetail(SavedCalculationSummary summary, CalculateRequest request, CalculateResponse response) {
        this.summary = summary;
        this.request = request;
        this.response = response;
    }

}
