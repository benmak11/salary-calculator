package app.salary.api.controller;

import app.salary.api.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/users")
@Tag(name = "Users", description = "User profile and preference endpoints")
public class UsersController {

    // ── GET /v1/users/me ────────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Return the authenticated user's profile")
    public ResponseEntity<UserProfileResponse> getProfile() {
        // TODO: Extract user ID from JWT and load from user-service or Firestore.
        //       Currently returns a hardcoded stub profile.
        return ResponseEntity.ok(
            new UserProfileResponse("usr_placeholder", "Julian Vance",
                "julian.v@incomatic.com", null)
        );
    }

    // ── PATCH /v1/users/me ──────────────────────────────────────────────────────

    @PatchMapping("/me")
    @Operation(summary = "Partially update the authenticated user's profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @RequestBody UserProfileUpdateRequest request) {
        // TODO: Persist updated fields to user-service / Firestore.
        String displayName = request.getDisplayName() != null ? request.getDisplayName() : "Julian Vance";
        String email       = request.getEmail()       != null ? request.getEmail()       : "julian.v@incomatic.com";
        return ResponseEntity.ok(
            new UserProfileResponse("usr_placeholder", displayName, email, null)
        );
    }

    // ── GET /v1/users/me/security ────────────────────────────────────────────────

    @GetMapping("/me/security")
    @Operation(summary = "Return the authenticated user's security settings")
    public ResponseEntity<UserSecurityResponse> getSecurity() {
        // TODO: Load from user-service / Firestore.
        return ResponseEntity.ok(new UserSecurityResponse(false, true));
    }

    // ── PUT /v1/users/me/security/biometrics ─────────────────────────────────────

    @PutMapping("/me/security/biometrics")
    @Operation(summary = "Sync biometric login preference to the backend")
    public ResponseEntity<Void> updateBiometrics(
            @RequestBody Map<String, Boolean> body) {
        // TODO: Persist to user-service / Firestore.
        return ResponseEntity.noContent().build();
    }

    // ── GET /v1/users/me/preferences ─────────────────────────────────────────────

    @GetMapping("/me/preferences")
    @Operation(summary = "Return the authenticated user's application preferences")
    public ResponseEntity<UserPreferencesResponse> getPreferences() {
        // TODO: Load from user-service / Firestore.
        return ResponseEntity.ok(new UserPreferencesResponse());
    }

    // ── PUT /v1/users/me/preferences ─────────────────────────────────────────────

    @PutMapping("/me/preferences")
    @Operation(summary = "Update the authenticated user's application preferences")
    public ResponseEntity<UserPreferencesResponse> updatePreferences(
            @RequestBody UserPreferencesResponse request) {
        // TODO: Persist to user-service / Firestore.
        return ResponseEntity.ok(request);
    }
}
