package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * A single analytics event within a batch.
 *
 * <p>Property values carry <b>bucketed</b> figures, never raw ones:
 * {@code net_bucket: "2000-3000"}, not {@code 2847.13}. A first-party analytics store
 * holding exact salaries is a materially worse breach than one holding ranges. The
 * server rejects obviously raw values, but the real contract is client-side.
 */
@ExcludeFromCodeCoverage
@Setter
@Getter
@Schema(description = "A single analytics event")
public class AnalyticsEvent {
    @NotBlank
    @Pattern(regexp = "[a-z][a-z0-9_]{0,49}",
            message = "must be lower_snake_case, 1-50 characters, starting with a letter")
    @Schema(description = "Event name", example = "calculation_completed")
    private String name;

    @Schema(description = "When the event happened on the client (ISO-8601 instant). "
            + "Defaults to server receive time when absent or unparseable.",
            example = "2026-08-05T18:04:11Z")
    private String occurredAt;

    @Size(max = 20, message = "an event may carry at most 20 properties")
    @Schema(description = "Bucketed, non-identifying event properties")
    private Map<String, Object> properties;
}
