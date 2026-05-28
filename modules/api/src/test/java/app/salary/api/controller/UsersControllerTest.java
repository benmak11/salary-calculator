package app.salary.api.controller;

import app.salary.api.dto.UserPreferencesResponse;
import app.salary.api.dto.UserProfileResponse;
import app.salary.api.dto.UserProfileUpdateRequest;
import app.salary.api.dto.UserSecurityResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UsersControllerTest {

    private UsersController controller;

    @BeforeEach
    void setUp() {
        controller = new UsersController();
    }

    @Test
    void getProfile_shouldReturnProfile() {
        ResponseEntity<UserProfileResponse> response = controller.getProfile();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getId());
        assertNotNull(response.getBody().getDisplayName());
        assertNotNull(response.getBody().getEmail());
    }

    @Test
    void updateProfile_withNewDisplayName_shouldReturnUpdatedProfile() {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setDisplayName("New Name");

        ResponseEntity<UserProfileResponse> response = controller.updateProfile(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("New Name", response.getBody().getDisplayName());
    }

    @Test
    void updateProfile_withNullFields_shouldReturnDefaults() {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();

        ResponseEntity<UserProfileResponse> response = controller.updateProfile(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getDisplayName());
    }

    @Test
    void getSecurity_shouldReturnSecuritySettings() {
        ResponseEntity<UserSecurityResponse> response = controller.getSecurity();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getTwoFactorEnabled());
        assertNotNull(response.getBody().getBiometricEnabled());
    }

    @Test
    void updateBiometrics_shouldReturn204() {
        ResponseEntity<Void> response = controller.updateBiometrics(
            Map.of("biometricEnabled", true));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void getPreferences_shouldReturnDefaultPreferences() {
        ResponseEntity<UserPreferencesResponse> response = controller.getPreferences();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getLanguage());
        assertNotNull(response.getBody().getAppearance());
        assertNotNull(response.getBody().getNotifications());
    }

    @Test
    void updatePreferences_shouldReturnUpdatedPreferences() {
        UserPreferencesResponse prefs = new UserPreferencesResponse();
        prefs.setLanguage("fr-FR");
        prefs.setAppearance("dark");

        ResponseEntity<UserPreferencesResponse> response = controller.updatePreferences(prefs);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("fr-FR", response.getBody().getLanguage());
        assertEquals("dark", response.getBody().getAppearance());
    }
}
