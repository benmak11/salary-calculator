package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A batch of analytics events from one device.
 *
 * <p>Batched rather than one-request-per-event because the app is used on flaky
 * connections and events queue offline until they can be flushed.
 */
@ExcludeFromCodeCoverage
@Setter
@Getter
@Schema(description = "A batch of analytics events from a single device")
public class EventBatchRequest {
    /**
     * Client-generated, stable per install. Present on every batch including anonymous
     * ones — most users are signed out through onboarding and their first calculation,
     * and without a device id the top of the funnel is invisible.
     */
    @NotBlank
    @Size(max = 64)
    @Schema(description = "Client-generated anonymous device id, stable per install",
            example = "3f9c1a7e-5b2d-4c8a-9e01-7d6f2b4a8c33")
    private String deviceId;

    @Size(max = 40)
    @Schema(description = "Client platform and version, matching the X-Incomatic-Client header",
            example = "ios/1.9.0")
    private String client;

    @NotEmpty
    @Size(max = 50, message = "a batch may carry at most 50 events")
    @Valid
    @Schema(description = "The events in this batch")
    private List<AnalyticsEvent> events;
}
