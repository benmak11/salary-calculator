package app.salary.api.controller;

import app.salary.api.validation.RequestValidator;
import app.salary.api.validation.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Javalin app() {
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(MAPPER, false));
            config.startup.showJavalinBanner = false;
            config.routes.exception(ValidationException.class, (e, ctx) ->
                    ctx.status(HttpStatus.BAD_REQUEST).json(e.getErrors()));
            new AuthController(new RequestValidator()).register(config.routes);
        });
    }

    @Test
    void login_withValidPayload_returnsAuthTokens() {
        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/v1/auth/login",
                    Map.of("email", "user@example.com", "password", "secret"));
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertNotNull(body.get("accessToken"));
            assertTrue(body.get("accessToken").asText().startsWith("stub_access_"));
            assertTrue(body.get("refreshToken").asText().startsWith("stub_refresh_"));
            assertEquals(3600, body.get("expiresIn").asInt());
        });
    }

    @Test
    void logout_returns204() {
        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/v1/auth/logout");
            assertEquals(204, response.code());
        });
    }

    @Test
    void refresh_withValidToken_returnsNewAccessToken() {
        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/v1/auth/refresh", Map.of("refreshToken", "stub_refresh_xyz"));
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertTrue(body.get("accessToken").asText().startsWith("stub_access_"));
            assertEquals("stub_refresh_xyz", body.get("refreshToken").asText());
            assertEquals(3600, body.get("expiresIn").asInt());
        });
    }

    @Test
    void refresh_withMissingToken_returns400() {
        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/v1/auth/refresh", Map.of());
            assertEquals(400, response.code());
        });
    }

    @Test
    void setup2fa_returnsQrAndSecret() {
        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/v1/auth/2fa/setup");
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertTrue(body.get("qrCodeUrl").asText().startsWith("otpauth://"));
            assertNotNull(body.get("secret"));
        });
    }

    @Test
    void verify2fa_returnsBackupCodes() {
        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/v1/auth/2fa/verify");
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertTrue(body.get("backupCodes").isArray());
            assertEquals(4, body.get("backupCodes").size());
        });
    }

    @Test
    void disable2fa_returns204() {
        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/v1/auth/2fa/disable");
            assertEquals(204, response.code());
        });
    }
}
