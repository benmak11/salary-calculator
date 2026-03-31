package app.salary.api.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

@ExcludeFromCodeCoverage
@Schema(description = "Authenticated user profile")
public class UserProfileResponse {
    @Schema(example = "usr_abc123")
    private String id;
    @Schema(example = "Julian Vance")
    private String displayName;
    @Schema(example = "julian.v@incomatic.com")
    private String email;
    @Schema(example = "https://storage.googleapis.com/...")
    private String profilePhotoUrl;

    public UserProfileResponse() {}

    public UserProfileResponse(String id, String displayName, String email, String profilePhotoUrl) {
        this.id = id;
        this.displayName = displayName;
        this.email = email;
        this.profilePhotoUrl = profilePhotoUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getProfilePhotoUrl() { return profilePhotoUrl; }
    public void setProfilePhotoUrl(String profilePhotoUrl) { this.profilePhotoUrl = profilePhotoUrl; }
}
