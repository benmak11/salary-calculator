package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@ExcludeFromCodeCoverage
@Schema(description = "Server-issued session credentials returned after successful Google sign-in")
public class GoogleSignInResponse {
    @Schema(description = "Bearer token for subsequent authenticated requests")
    private String sessionToken;

    @Schema(description = "Session-token expiry in ISO-8601 UTC", example = "2026-07-09T15:23:00Z")
    private String expiresAt;

    @Schema(description = "Account profile")
    private User user;

    public GoogleSignInResponse() {}

    public GoogleSignInResponse(String sessionToken, String expiresAt, User user) {
        this.sessionToken = sessionToken;
        this.expiresAt = expiresAt;
        this.user = user;
    }

    @Getter
    @Setter
    @ExcludeFromCodeCoverage
    @Schema(description = "Minimal profile returned to the client")
    public static class User {
        @Schema(description = "Stable internal user id (currently equals the Google sub claim)")
        private String id;

        @Schema(description = "Display name supplied by Google on first sign-in; null otherwise")
        private String displayName;

        public User() {}

        public User(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
    }
}
