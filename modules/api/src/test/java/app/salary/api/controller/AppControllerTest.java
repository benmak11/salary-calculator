package app.salary.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Javalin app() {
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(MAPPER, false));
            config.startup.showJavalinBanner = false;
            new AppController().register(config.routes);
        });
    }

    @Test
    void getLegal_returnsAllLegalUrls() {
        JavalinTest.test(app(), (server, client) -> {
            var response = client.get("/v1/app/legal");
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("https://help.incomatic.app",    body.get("helpCenterUrl").asText());
            assertEquals("https://incomatic.app/privacy", body.get("privacyPolicyUrl").asText());
            assertEquals("https://incomatic.app/terms",   body.get("termsUrl").asText());
        });
    }

    @Test
    void getVersion_returnsVersionMetadata() {
        JavalinTest.test(app(), (server, client) -> {
            var response = client.get("/v1/app/version");
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertNotNull(body.get("latestVersion"));
            assertNotNull(body.get("minimumSupportedVersion"));
            assertTrue(body.has("forceUpgrade"));
            assertNotNull(body.get("releaseNotesUrl"));
        });
    }
}
