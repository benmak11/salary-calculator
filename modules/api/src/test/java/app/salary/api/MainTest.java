package app.salary.api;

import app.salary.api.controller.AccountController;
import app.salary.api.controller.BudgetController;
import app.salary.api.controller.CalculationHistoryController;
import app.salary.api.controller.GrantsController;
import app.salary.api.controller.StocksController;
import app.salary.api.store.BudgetStore;
import app.salary.api.store.CalculationStore;
import app.salary.api.store.GrantStore;
import app.salary.api.store.InMemoryBudgetStore;
import app.salary.api.store.InMemoryCalculationStore;
import app.salary.api.store.InMemoryGrantStore;
import app.salary.api.store.InMemoryUserDirectory;
import app.salary.api.store.UserDirectory;
import app.salary.api.validation.RequestValidator;
import app.salary.calculator.countries.USCalculator;
import app.salary.calculator.engine.CalculationOrchestrator;
import app.salary.calculator.engine.CountryCalculator;
import app.salary.calculator.registry.CalculatorRegistry;
import app.salary.calculator.shared.DeductionCalculator;
import app.salary.calculator.shared.TaxBracketCalculator;
import app.salary.rules.RulesRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for the api {@link Main} app wiring under Javalin 7.
 *
 * Confirms that {@link Main#createApp} successfully constructs a Javalin app with
 * all routes registered through {@code config.routes.xxx} (the v7 contract).
 */
class MainTest {

    private static Javalin createApp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        TaxBracketCalculator brackets = new TaxBracketCalculator();
        DeductionCalculator deductions = new DeductionCalculator();
        List<CountryCalculator> calculators = List.of(new USCalculator(brackets, deductions));
        CalculatorRegistry calculatorRegistry = new CalculatorRegistry(calculators);
        RulesRegistry rulesRegistry = new RulesRegistry();
        CalculationOrchestrator orchestrator = new CalculationOrchestrator(
                rulesRegistry, calculatorRegistry, null, meterRegistry);
        RequestValidator validator = new RequestValidator();

        CalculationStore calculationStore = new InMemoryCalculationStore();
        GrantStore grantStore = new InMemoryGrantStore();
        BudgetStore budgetStore = new InMemoryBudgetStore();
        UserDirectory userDirectory = new InMemoryUserDirectory();
        CalculationHistoryController historyController = new CalculationHistoryController(calculationStore);
        AccountController accountController =
                new AccountController(calculationStore, grantStore, budgetStore, userDirectory);
        GrantsController grantsController = new GrantsController(grantStore, validator);
        BudgetController budgetController = new BudgetController(budgetStore, validator);
        StocksController stocksController = new StocksController(null);
        return Main.createApp(mapper, meterRegistry, orchestrator, calculatorRegistry, validator,
                calculationStore, null, null, historyController, accountController, grantsController,
                budgetController, stocksController);
    }

    @Test
    void supportedCountries_isReachable() {
        JavalinTest.test(createApp(), (server, client) -> {
            var response = client.get("/v1/countries");
            assertEquals(200, response.code());
            JsonNode body = new ObjectMapper().readTree(response.body().string());
            assertEquals(1, body.get("count").asInt());
            assertEquals("US", body.get("countries").get(0).asText());
        });
    }

    @Test
    void usStates_isReachable() {
        JavalinTest.test(createApp(), (server, client) -> {
            var response = client.get("/v1/countries/US/states");
            assertEquals(200, response.code());
            JsonNode body = new ObjectMapper().readTree(response.body().string());
            assertTrue(body.isArray());
            assertEquals(51, body.size());
        });
    }

    @Test
    void taxYears_isReachable() {
        JavalinTest.test(createApp(), (server, client) -> {
            var response = client.get("/v1/tax-years");
            assertEquals(200, response.code());
            JsonNode body = new ObjectMapper().readTree(response.body().string());
            assertEquals("US", body.get("country").asText());
            assertEquals(2025, body.get("defaultTaxYear").asInt());
            assertTrue(body.get("supportedTaxYears").isArray());
        });
    }

    @Test
    void taxYears_uppercasesCountryQueryParam() {
        JavalinTest.test(createApp(), (server, client) -> {
            var response = client.get("/v1/tax-years?country=uk");
            assertEquals(200, response.code());
            JsonNode body = new ObjectMapper().readTree(response.body().string());
            assertEquals("UK", body.get("country").asText());
        });
    }

    @Test
    void actuatorHealth_isReachable() {
        JavalinTest.test(createApp(), (server, client) -> {
            var response = client.get("/actuator/health");
            assertEquals(200, response.code());
            JsonNode body = new ObjectMapper().readTree(response.body().string());
            assertEquals("UP", body.get("status").asText());
        });
    }

    @Test
    void actuatorPrometheus_servesScrapeFormat() {
        JavalinTest.test(createApp(), (server, client) -> {
            var response = client.get("/actuator/prometheus");
            assertEquals(200, response.code());
            String body = response.body().string();
            assertNotNull(body);
            assertTrue(body.contains("# HELP") || body.isEmpty());
        });
    }
}
