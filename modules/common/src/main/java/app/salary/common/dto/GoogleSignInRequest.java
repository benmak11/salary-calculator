package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Setter
@Getter
@ExcludeFromCodeCoverage
@Schema(description = "Exchange a Google ID token for a server-issued session token")
public class GoogleSignInRequest {
    @NotBlank
    @Schema(description = "ID token returned by Android's Credential Manager Google Sign-In flow")
    private String idToken;

    @Schema(description = "Display name supplied by the client on first sign-in only; null otherwise")
    private String displayName;

    public GoogleSignInRequest() {}

}
