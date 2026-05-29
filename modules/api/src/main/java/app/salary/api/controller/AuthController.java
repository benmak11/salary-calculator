package app.salary.api.controller;

import app.salary.api.dto.AuthLoginRequest;
import app.salary.api.dto.AuthResponse;
import app.salary.api.validation.RequestValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class AuthController {

    private static final TypeReference<Map<String, String>> STRING_MAP =
            new TypeReference<>() {};

    private final RequestValidator validator;

    public AuthController(RequestValidator validator) {
        this.validator = validator;
    }

    public void register(RoutesConfig routes) {
        routes.post("/v1/auth/login",       this::login);
        routes.post("/v1/auth/logout",      this::logout);
        routes.post("/v1/auth/refresh",     this::refresh);
        routes.post("/v1/auth/2fa/setup",   this::setup2fa);
        routes.post("/v1/auth/2fa/verify",  this::verify2fa);
        routes.post("/v1/auth/2fa/disable", this::disable2fa);
    }

    private void login(Context ctx) {
        AuthLoginRequest request = ctx.bodyAsClass(AuthLoginRequest.class);
        validator.validate(request);

        // TODO: Validate credentials against user-service / identity provider.
        //       Issue real JWTs signed with a server secret. Currently returns stub tokens.
        String accessToken  = "stub_access_"  + UUID.randomUUID().toString().replace("-", "");
        String refreshToken = "stub_refresh_" + UUID.randomUUID().toString().replace("-", "");
        ctx.json(new AuthResponse(accessToken, refreshToken, 3600));
    }

    private void logout(Context ctx) {
        // TODO: Revoke the refresh token in the token store / identity provider.
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void refresh(Context ctx) {
        Map<String, String> body = ctx.bodyAsClass(STRING_MAP.getType());
        String refreshToken = body != null ? body.getOrDefault("refreshToken", "") : "";
        if (refreshToken.isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST);
            return;
        }
        String newAccessToken = "stub_access_" + UUID.randomUUID().toString().replace("-", "");
        ctx.json(new AuthResponse(newAccessToken, refreshToken, 3600));
    }

    private void setup2fa(Context ctx) {
        // TODO: Generate real TOTP secret via an authenticator library.
        ctx.json(Map.of(
                "qrCodeUrl", "otpauth://totp/Incomatic?secret=JBSWY3DPEHPK3PXP&issuer=Incomatic",
                "secret",    "JBSWY3DPEHPK3PXP"
        ));
    }

    private void verify2fa(Context ctx) {
        // TODO: Validate code against stored TOTP secret.
        ctx.json(Map.of(
                "backupCodes", new String[]{"12345678", "87654321", "11223344", "44332211"}
        ));
    }

    private void disable2fa(Context ctx) {
        // TODO: Validate code then disable 2FA flag on user record.
        ctx.status(HttpStatus.NO_CONTENT);
    }
}
