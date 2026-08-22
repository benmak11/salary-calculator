package app.salary.api;

import app.salary.api.controller.AccountController;
import app.salary.api.controller.BudgetController;
import app.salary.api.controller.BudgetPlanController;
import app.salary.api.controller.CalculationHistoryController;
import app.salary.api.controller.EventsController;
import app.salary.api.controller.GrantsController;
import app.salary.api.controller.StocksController;
import app.salary.api.store.BudgetStore;
import app.salary.api.store.CalculationStore;
import app.salary.api.store.GrantStore;
import app.salary.api.controller.AccountLinkController;
import app.salary.api.controller.CheckInController;
import app.salary.api.store.AccountKeyedStores;
import app.salary.api.store.InMemoryCheckInStore;
import app.salary.api.store.SubKeyedStores;
import app.salary.api.store.InMemoryAccountDirectory;
import app.salary.api.version.ClientVersionMiddleware;
import app.salary.api.store.InMemoryEntitlementStore;
import app.salary.api.store.InMemoryLinkCodeStore;
import app.salary.api.store.InMemoryBudgetStore;
import app.salary.api.store.InMemoryEventStore;
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
import io.javalin.testtools.Request;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
                new AccountController(new InMemoryAccountDirectory(),
                        new SubKeyedStores(calculationStore, grantStore, budgetStore, userDirectory),
                        new AccountKeyedStores(new InMemoryEntitlementStore(),
                                new InMemoryLinkCodeStore(), new InMemoryCheckInStore()));
        GrantsController grantsController = new GrantsController(grantStore, validator);
        BudgetController budgetController = new BudgetController(budgetStore, validator);
        BudgetPlanController budgetPlanController = new BudgetPlanController(null, validator, null, null);
        StocksController stocksController = new StocksController(null);
        EventsController eventsController =
                new EventsController(new InMemoryEventStore(), new InMemoryAccountDirectory(), validator);
        return Main.createApp(mapper, meterRegistry, orchestrator, calculatorRegistry, validator,
                calculationStore, rulesRegistry, null, null, historyController, accountController, grantsController,
                budgetController, budgetPlanController, stocksController, eventsController, null,
                new ClientVersionMiddleware(Map.of()),
                new AccountLinkController(new InMemoryLinkCodeStore(), new InMemoryAccountDirectory(), null, null),
                new CheckInController(new InMemoryCheckInStore(), new InMemoryAccountDirectory(), validator));
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
            // Newest embedded pack wins; see CalculateControllerTest for why this
            // asserts a floor rather than an exact year.
            assertTrue(body.get("defaultTaxYear").asInt() >= 2026);
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

    // ── malformed bodies ─────────────────────────────────────────────────────
    // These went out as 500s until the Jackson parse handlers were registered, which
    // reads as "the service is broken" when the caller simply sent the wrong shape.

    @Test
    void calculate_withUnacceptedEnumValue_returns400NamingTheField() {
        JavalinTest.test(createApp(), (server, client) -> {
            var response = client.post("/v1/calculate", Map.of(
                    "country", "US",
                    "taxYear", 2026,
                    "cadence", "ANNUAL",
                    // Basis is PER_YEAR / PER_PERIOD; "ANNUAL" is the cadence vocabulary.
                    "earnings", Map.of("salary", Map.of("amount", 100000, "basis", "ANNUAL"))));

            assertEquals(400, response.code());
            JsonNode body = new ObjectMapper().readTree(response.body().string());
            String message = body.get("earnings.salary.basis").asText();
            assertTrue(message.contains("PER_YEAR"), message);
            assertTrue(message.contains("PER_PERIOD"), message);
        });
    }

    @Test
    void calculate_withUnsupportedTaxYear_returns422NotServerError() {
        // @Min(2025) has no upper bound, so a year we ship no rule pack for
        // passes validation and then fails to load. That used to reach the
        // catch-all and surface as "Internal server error" - telling the caller
        // our service was broken when the request was simply for a year that
        // does not exist. It also decides what retiring a tax year looks like:
        // every client still pinned to it would otherwise see a 500.
        //
        // Deliberately in MainTest rather than CalculateControllerTest, because
        // that harness builds its own Javalin with a hand-copied subset of the
        // exception handlers. A handler added to Main is invisible there.
        JavalinTest.test(createApp(), (server, client) -> {
            var response = client.post("/v1/calculate", Map.of(
                    "country", "US",
                    "taxYear", 2099,
                    "earnings", Map.of("salary", Map.of("amount", 100000, "basis", "PER_YEAR")),
                    "countryOptions", Map.of("US", Map.of("state", "CA", "filingStatus", "SINGLE"))));

            assertEquals(422, response.code());
            JsonNode body = new ObjectMapper().readTree(response.body().string());
            assertEquals("unsupported_tax_year", body.get("error").asText());
            // The years that do work are named, because a client pinned to a
            // retired year cannot otherwise tell what to ask for instead.
            assertTrue(body.get("supportedTaxYears").isArray());
            assertTrue(body.get("supportedTaxYears").size() > 0);
        });
    }

    @Test
    void calculate_withUnparseableBody_returns400() {
        JavalinTest.test(createApp(), (server, client) -> {
            // request(), not post(): post() applies its own body publisher AFTER the caller's
            // customizer runs, so a raw body set there is silently replaced by the serialized
            // `json` argument (noBody() when that argument is null).
            var response = client.request("/v1/calculate", (Consumer<Request.Builder>) req -> req
                    .post(HttpRequest.BodyPublishers.ofString("{not json"))
                    .header("Content-Type", "application/json"));

            assertEquals(400, response.code());
            JsonNode body = new ObjectMapper().readTree(response.body().string());
            assertEquals("is not valid JSON", body.get("body").asText());
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
