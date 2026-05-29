package app.salary.api.controller;

import app.salary.api.service.CalculationStore;
import app.salary.common.dto.CalculateResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReportsControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Javalin appWith(CalculationStore store) {
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(MAPPER, false));
            config.startup.showJavalinBanner = false;
            new ReportsController(store).register(config.routes);
        });
    }

    @Test
    void generatePdf_withBlankId_returns400() {
        JavalinTest.test(appWith(new CalculationStore()), (server, client) -> {
            var response = client.post("/v1/reports/pdf", Map.of("calculationId", ""));
            assertEquals(400, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("calculationId is required", body.get("error").asText());
        });
    }

    @Test
    void generatePdf_withUnknownId_returns404() {
        JavalinTest.test(appWith(new CalculationStore()), (server, client) -> {
            var response = client.post("/v1/reports/pdf", Map.of("calculationId", "c_missing"));
            assertEquals(404, response.code());
        });
    }

    @Test
    void generatePdf_withKnownId_returns202Pending() {
        CalculationStore store = new CalculationStore();
        CalculateResponse calc = new CalculateResponse();
        calc.setCalculationId("c_pdf");
        store.store(calc);

        JavalinTest.test(appWith(store), (server, client) -> {
            var response = client.post("/v1/reports/pdf", Map.of("calculationId", "c_pdf"));
            assertEquals(202, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("PENDING", body.get("status").asText());
            assertEquals("c_pdf",   body.get("calculationId").asText());
            assertNotNull(body.get("message"));
        });
    }
}
