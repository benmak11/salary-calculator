package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** Acknowledges how many events were stored. */
@ExcludeFromCodeCoverage
@Setter
@Getter
@Schema(description = "Analytics ingest acknowledgement")
public class EventBatchResponse {
    @Schema(description = "How many events were stored", example = "12")
    private int accepted;

    public EventBatchResponse() {}

    public EventBatchResponse(int accepted) {
        this.accepted = accepted;
    }
}
