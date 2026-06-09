package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Setter
@Getter
@ExcludeFromCodeCoverage
@Schema(description = "Exchange an Apple identity token for a server-issued session token")
public class AppleSignInRequest {
    @NotBlank
    @Schema(description = "JWT identity token returned by ASAuthorizationAppleIDProvider")
    private String identityToken;

    @NotBlank
    @Schema(description = "Random nonce the client generated before the Apple sign-in; bound to the identity token's nonce claim")
    private String nonce;

    @Schema(description = "Display name supplied by Apple on first sign-in only; null otherwise")
    private String displayName;

    public AppleSignInRequest() {}

}
