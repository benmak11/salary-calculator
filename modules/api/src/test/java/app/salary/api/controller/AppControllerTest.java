package app.salary.api.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AppControllerTest {

    private AppController controller;

    @BeforeEach
    void setUp() {
        controller = new AppController();
    }

    @Test
    void getLegal_shouldReturnThreeUrls() {
        ResponseEntity<Map<String, String>> response = controller.getLegal();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("helpCenterUrl"));
        assertTrue(response.getBody().containsKey("privacyPolicyUrl"));
        assertTrue(response.getBody().containsKey("termsUrl"));
    }

    @Test
    void getLegal_urlsShouldBeNonBlank() {
        ResponseEntity<Map<String, String>> response = controller.getLegal();

        assertNotNull(response.getBody());
        response.getBody().values().forEach(url -> assertFalse(url.isBlank()));
    }

    @Test
    void getVersion_shouldReturnRequiredFields() {
        ResponseEntity<Map<String, Object>> response = controller.getVersion();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("latestVersion"));
        assertTrue(response.getBody().containsKey("minimumSupportedVersion"));
        assertTrue(response.getBody().containsKey("forceUpgrade"));
        assertTrue(response.getBody().containsKey("releaseNotesUrl"));
    }

    @Test
    void getVersion_forceUpgrade_shouldBeFalseByDefault() {
        ResponseEntity<Map<String, Object>> response = controller.getVersion();

        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().get("forceUpgrade"));
    }
}
