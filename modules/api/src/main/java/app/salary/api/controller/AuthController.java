package app.salary.api.controller;

import app.salary.api.dto.AuthLoginRequest;
import app.salary.api.dto.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Auth", description = "Authentication endpoints")
public class AuthController {

    // ── POST /v1/auth/login ─────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password; returns JWT access and refresh tokens")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        // TODO: Validate credentials against user-service / identity provider (Firebase Auth, etc.).
        //       Issue real JWTs signed with a server secret. Currently returns stub tokens.
        String accessToken  = "stub_access_"  + UUID.randomUUID().toString().replace("-", "");
        String refreshToken = "stub_refresh_" + UUID.randomUUID().toString().replace("-", "");
        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken, 3600));
    }

    // ── POST /v1/auth/logout ────────────────────────────────────────────────────

    @PostMapping("/logout")
    @Operation(summary = "Invalidate the provided refresh token")
    public ResponseEntity<Void> logout(@RequestBody Map<String, String> body) {
        // TODO: Revoke the refresh token in the token store / identity provider.
        return ResponseEntity.noContent().build();
    }

    // ── POST /v1/auth/refresh ───────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token")
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        // TODO: Validate refresh token and issue a new access token.
        String refreshToken = body.getOrDefault("refreshToken", "");
        if (refreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String newAccessToken = "stub_access_" + UUID.randomUUID().toString().replace("-", "");
        return ResponseEntity.ok(new AuthResponse(newAccessToken, refreshToken, 3600));
    }

    // ── POST /v1/auth/2fa/setup ─────────────────────────────────────────────────

    @PostMapping("/2fa/setup")
    @Operation(summary = "Initiate TOTP 2FA setup; returns QR code URL and secret")
    public ResponseEntity<Map<String, String>> setup2fa() {
        // TODO: Generate real TOTP secret via an authenticator library.
        return ResponseEntity.ok(Map.of(
            "qrCodeUrl", "otpauth://totp/Incomatic?secret=JBSWY3DPEHPK3PXP&issuer=Incomatic",
            "secret",    "JBSWY3DPEHPK3PXP"
        ));
    }

    // ── POST /v1/auth/2fa/verify ────────────────────────────────────────────────

    @PostMapping("/2fa/verify")
    @Operation(summary = "Verify a TOTP code and enable 2FA; returns backup codes")
    public ResponseEntity<Map<String, Object>> verify2fa(@RequestBody Map<String, String> body) {
        // TODO: Validate code against stored TOTP secret.
        return ResponseEntity.ok(Map.of(
            "backupCodes", new String[]{"12345678", "87654321", "11223344", "44332211"}
        ));
    }

    // ── POST /v1/auth/2fa/disable ───────────────────────────────────────────────

    @PostMapping("/2fa/disable")
    @Operation(summary = "Disable 2FA after verifying the current TOTP code")
    public ResponseEntity<Void> disable2fa(@RequestBody Map<String, String> body) {
        // TODO: Validate code then disable 2FA flag on user record.
        return ResponseEntity.noContent().build();
    }
}
