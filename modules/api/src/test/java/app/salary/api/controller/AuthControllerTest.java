package app.salary.api.controller;

import app.salary.api.dto.AuthLoginRequest;
import app.salary.api.dto.AuthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthControllerTest {

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController();
    }

    @Test
    void login_withCredentials_shouldReturnTokens() {
        AuthLoginRequest request = new AuthLoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");

        ResponseEntity<AuthResponse> response = controller.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getAccessToken());
        assertNotNull(response.getBody().getRefreshToken());
        assertEquals(3600, response.getBody().getExpiresIn());
    }

    @Test
    void login_tokensShouldBeDistinct() {
        AuthLoginRequest request = new AuthLoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");

        ResponseEntity<AuthResponse> response = controller.login(request);

        assertNotNull(response.getBody());
        assertNotEquals(response.getBody().getAccessToken(), response.getBody().getRefreshToken());
    }

    @Test
    void logout_shouldReturn204() {
        ResponseEntity<Void> response = controller.logout(Map.of("refreshToken", "tok_abc"));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void refresh_withValidRefreshToken_shouldReturnNewAccessToken() {
        ResponseEntity<AuthResponse> response = controller.refresh(Map.of("refreshToken", "tok_existing"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getAccessToken());
        assertEquals("tok_existing", response.getBody().getRefreshToken());
    }

    @Test
    void refresh_withBlankRefreshToken_shouldReturn400() {
        ResponseEntity<AuthResponse> response = controller.refresh(Map.of("refreshToken", ""));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void setup2fa_shouldReturnQrCodeAndSecret() {
        ResponseEntity<Map<String, String>> response = controller.setup2fa();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("qrCodeUrl"));
        assertTrue(response.getBody().containsKey("secret"));
    }

    @Test
    void verify2fa_shouldReturnBackupCodes() {
        ResponseEntity<Map<String, Object>> response =
            controller.verify2fa(Map.of("code", "123456"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("backupCodes"));
    }

    @Test
    void disable2fa_shouldReturn204() {
        ResponseEntity<Void> response = controller.disable2fa(Map.of("code", "123456"));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }
}
