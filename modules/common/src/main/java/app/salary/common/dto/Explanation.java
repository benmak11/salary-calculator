package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

@ExcludeFromCodeCoverage
@Schema(description = "Human-readable explanation of a calculation step")
public class Explanation {
    @Schema(description = "Unique identifier for this explanation", example = "fed_tax_brackets")
    private String id;

    @Schema(description = "Human-readable explanation text", example = "Applied 2025 federal tax brackets based on SINGLE")
    private String text;

    public Explanation() {}

    public Explanation(String id, String text) {
        this.id = id;
        this.text = text;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
