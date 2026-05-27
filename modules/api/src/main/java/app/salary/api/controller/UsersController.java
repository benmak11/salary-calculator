package app.salary.api.controller;

import app.salary.api.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.Map;

public class UsersController {

    private static final TypeReference<Map<String, Boolean>> BOOLEAN_MAP =
            new TypeReference<>() {};

    public void register(Javalin app) {
        app.get(   "/v1/users/me",                       this::getProfile);
        app.patch( "/v1/users/me",                       this::updateProfile);
        app.get(   "/v1/users/me/security",              this::getSecurity);
        app.put(   "/v1/users/me/security/biometrics",   this::updateBiometrics);
        app.get(   "/v1/users/me/preferences",           this::getPreferences);
        app.put(   "/v1/users/me/preferences",           this::updatePreferences);
    }

    private void getProfile(Context ctx) {
        // TODO: Extract user ID from JWT and load from user-service or Firestore.
        ctx.json(new UserProfileResponse("usr_placeholder", "Julian Vance",
                "julian.v@incomatic.com", null));
    }

    private void updateProfile(Context ctx) {
        UserProfileUpdateRequest request = ctx.bodyAsClass(UserProfileUpdateRequest.class);
        // TODO: Persist updated fields to user-service / Firestore.
        String displayName = request.getDisplayName() != null ? request.getDisplayName() : "Julian Vance";
        String email       = request.getEmail()       != null ? request.getEmail()       : "julian.v@incomatic.com";
        ctx.json(new UserProfileResponse("usr_placeholder", displayName, email, null));
    }

    private void getSecurity(Context ctx) {
        // TODO: Load from user-service / Firestore.
        ctx.json(new UserSecurityResponse(false, true));
    }

    private void updateBiometrics(Context ctx) {
        Map<String, Boolean> body = ctx.bodyAsClass(BOOLEAN_MAP.getType());
        // TODO: Persist to user-service / Firestore (body = {biometricEnabled: true|false})
        if (body == null || !body.containsKey("biometricEnabled")) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "biometricEnabled is required"));
            return;
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void getPreferences(Context ctx) {
        // TODO: Load from user-service / Firestore.
        ctx.json(new UserPreferencesResponse());
    }

    private void updatePreferences(Context ctx) {
        UserPreferencesResponse request = ctx.bodyAsClass(UserPreferencesResponse.class);
        // TODO: Persist to user-service / Firestore.
        ctx.json(request);
    }
}
