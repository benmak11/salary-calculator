package app.salary.api.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

@ExcludeFromCodeCoverage
@Schema(description = "Authentication token response")
public class AuthResponse {
    @Schema(example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;
    @Schema(example = "dGhpcyBpcyBhIHJlZnJlc2g...")
    private String refreshToken;
    @Schema(example = "3600", description = "Access token expiry in seconds")
    private Integer expiresIn;

    public AuthResponse() {}

    public AuthResponse(String accessToken, String refreshToken, Integer expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public Integer getExpiresIn() { return expiresIn; }
    public void setExpiresIn(Integer expiresIn) { this.expiresIn = expiresIn; }
}
