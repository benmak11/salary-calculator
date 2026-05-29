package app.salary.api.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@ExcludeFromCodeCoverage
@Schema(description = "Login credentials")
public class AuthLoginRequest {

    @Schema(example = "julian.v@incomatic.com")
    private String email;

    @Schema(example = "••••••••")
    private String password;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
