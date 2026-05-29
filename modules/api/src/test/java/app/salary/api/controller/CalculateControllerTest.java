package app.salary.api.controller;

import app.salary.api.validation.RequestValidator;
import app.salary.api.validation.ValidationException;
import app.salary.calculator.engine.CalculationOrchestrator;
import app.salary.calculator.registry.CalculatorRegistry;
import app.salary.common.constants.Country;
import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalculateControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CalculationOrchestrator orchestrator;
    private CalculatorRegistry calculatorRegistry;

    @BeforeEach
    void setUp() {
        orchestrator = mock(CalculationOrchestrator.class);
        calculatorRegistry = mock(CalculatorRegistry.class);
    }

    private Javalin app() {
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(MAPPER, false));
            config.startup.showJavalinBanner = false;
            config.routes.exception(ValidationException.class, (e, ctx) ->
                    ctx.status(HttpStatus.BAD_REQUEST).json(e.getErrors()));
            config.routes.exception(IllegalArgumentException.class, (e, ctx) ->
                    ctx.status(HttpStatus.UNPROCESSABLE_CONTENT)
                            .json(Map.of("error", String.valueOf(e.getMessage()))));
            new CalculateController(orchestrator, calculatorRegistry,
                    new RequestValidator()).register(config.routes);
        });
    }

    @Test
    void getSupportedCountries_returnsArray() {
        when(calculatorRegistry.getSupportedCountries()).thenReturn(List.of(Country.US));

        JavalinTest.test(app(), (server, client) -> {
            var response = client.get("/v1/countries");
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals(1, body.get("count").asInt());
            assertTrue(body.get("countries").isArray());
            assertEquals("US", body.get("countries").get(0).asText());
        });
    }

    @Test
    void getUsStates_returns51EntriesIncludingDC() {
        JavalinTest.test(app(), (server, client) -> {
            var response = client.get("/v1/countries/US/states");
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertTrue(body.isArray());
            assertEquals(51, body.size()); // 50 states + DC
            assertEquals("AL", body.get(0).get("code").asText());
            assertEquals("DC", body.get(50).get("code").asText());
        });
    }

    @Test
    void getSupportedTaxYears_defaultsToUS() {
        JavalinTest.test(app(), (server, client) -> {
            var response = client.get("/v1/tax-years");
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("US", body.get("country").asText());
            assertEquals(2025, body.get("supportedTaxYears").get(0).asInt());
            assertEquals(2025, body.get("defaultTaxYear").asInt());
        });
    }

    @Test
    void getSupportedTaxYears_honoursCountryParam() {
        JavalinTest.test(app(), (server, client) -> {
            var response = client.get("/v1/tax-years?country=uk");
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("UK", body.get("country").asText());
        });
    }

    @Test
    void calculate_invokesOrchestratorAndReturnsResponse() {
        CalculateResponse stub = new CalculateResponse();
        stub.setCalculationId("c_abc123");
        stub.setGrossPerCadence(100_000.0);
        stub.setNetPerCadence(72_000.0);
        when(orchestrator.calculate(any(CalculateRequest.class))).thenReturn(stub);

        JavalinTest.test(app(), (server, client) -> {
            String payload = """
                    {
                      "country":"US",
                      "taxYear":2025,
                      "annualSalary":100000,
                      "countryOptions":{"US":{"state":"CA","filingStatus":"SINGLE"}}
                    }
                    """;
            var response = client.post("/v1/calculate", payload);
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("c_abc123", body.get("calculationId").asText());
            assertEquals(100_000.0,  body.get("grossPerCadence").asDouble());

            verify(orchestrator, times(1)).calculate(any(CalculateRequest.class));
        });
    }

    @Test
    void calculate_withInvalidRequest_returns400() {
        JavalinTest.test(app(), (server, client) -> {
            // Missing required `country` and `taxYear` fields
            var response = client.post("/v1/calculate", Map.of("annualSalary", 50_000));
            assertEquals(400, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertTrue(body.has("country") || body.has("taxYear"));
        });
    }
}
