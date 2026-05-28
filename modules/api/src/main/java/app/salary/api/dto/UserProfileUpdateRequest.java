package app.salary.api.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

@ExcludeFromCodeCoverage
@Schema(description = "Fields eligible for a partial profile update (PATCH)")
public class UserProfileUpdateRequest {
    @Schema(example = "Julian Vance")
    private String displayName;
    @Schema(example = "julian.v@incomatic.com")
    private String email;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
