package app.salary.api.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

@ExcludeFromCodeCoverage
@Schema(description = "Security settings for the authenticated user")
public class UserSecurityResponse {
    @Schema(example = "false")
    private Boolean twoFactorEnabled;
    @Schema(example = "true")
    private Boolean biometricEnabled;

    public UserSecurityResponse() {}

    public UserSecurityResponse(Boolean twoFactorEnabled, Boolean biometricEnabled) {
        this.twoFactorEnabled = twoFactorEnabled;
        this.biometricEnabled = biometricEnabled;
    }

    public Boolean getTwoFactorEnabled() { return twoFactorEnabled; }
    public void setTwoFactorEnabled(Boolean twoFactorEnabled) { this.twoFactorEnabled = twoFactorEnabled; }
    public Boolean getBiometricEnabled() { return biometricEnabled; }
    public void setBiometricEnabled(Boolean biometricEnabled) { this.biometricEnabled = biometricEnabled; }
}
