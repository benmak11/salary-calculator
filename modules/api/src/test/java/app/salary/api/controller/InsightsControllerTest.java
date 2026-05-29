package app.salary.api.controller;

import app.salary.api.service.CalculationStore;
import app.salary.common.dto.CalculateResponse;
import app.salary.common.dto.LineItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsightsControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Javalin appWith(CalculationStore store) {
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(MAPPER, false));
            config.startup.showJavalinBanner = false;
            new InsightsController(store).register(config.routes);
        });
    }

    @Test
    void getInsight_unknownId_returns404() {
        JavalinTest.test(appWith(new CalculationStore()), (server, client) -> {
            var response = client.get("/v1/insights/c_missing");
            assertEquals(404, response.code());
        });
    }

    @Test
    void getInsight_knownId_returnsPopulatedInsight() {
        CalculationStore store = new CalculationStore();
        CalculateResponse calc = new CalculateResponse();
        calc.setCalculationId("c_known");
        calc.setGrossPerCadence(100_000.0);
        calc.setLineItems(List.of(
                new LineItem("Federal Income Tax", 13_841.0),
                new LineItem("Social Security",     6_200.0),
                new LineItem("Medicare",            1_450.0)
        ));
        store.store(calc);

        JavalinTest.test(appWith(store), (server, client) -> {
            var response = client.get("/v1/insights/c_known");
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("RETIREMENT_OPTIMIZATION", body.get("type").asText());
            assertEquals("Smart Saving Insight",    body.get("headline").asText());
            assertNotNull(body.get("monthlyTaxSavings"));
            assertNotNull(body.get("annualRetirementBenefit"));
            assertEquals(7.0, body.get("recommendedContributionPercent").asDouble(), 0.001);
            assertTrue(body.get("body").asText().contains("401(k)"));
            assertEquals("Optimize 401k", body.get("ctaLabel").asText());

            // Annual retirement benefit = 1% of $100k * (1 + 100% match) = $2000
            assertEquals(2000.0, body.get("annualRetirementBenefit").asDouble(), 0.01);
        });
    }

    @Test
    void getInsight_emptyLineItems_fallsBackToDefaultMarginalRate() {
        CalculationStore store = new CalculationStore();
        CalculateResponse calc = new CalculateResponse();
        calc.setCalculationId("c_empty");
        calc.setGrossPerCadence(50_000.0);
        store.store(calc);

        JavalinTest.test(appWith(store), (server, client) -> {
            var response = client.get("/v1/insights/c_empty");
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            // Default tax rate 0.22 → marginal min(0.22+0.05, 0.37) = 0.27
            // 1% of $50k = $500 contribution; $500 * 0.27 = $135 annual / 12 = $11.25/mo
            assertEquals(11.25, body.get("monthlyTaxSavings").asDouble(), 0.01);
        });
    }
}
