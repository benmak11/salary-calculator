package app.salary.api;

import app.salary.api.client.HttpRulePackClient;
import app.salary.api.controller.AppController;
import app.salary.api.controller.AuthController;
import app.salary.api.controller.BenefitsController;
import app.salary.api.controller.CalculateController;
import app.salary.api.controller.InsightsController;
import app.salary.api.controller.ReportsController;
import app.salary.api.controller.UsersController;
import app.salary.api.service.CalculationStore;
import app.salary.api.validation.RequestValidator;
import app.salary.api.validation.ValidationException;
import app.salary.calculator.client.RulePackClient;
import app.salary.calculator.countries.UKCalculator;
import app.salary.calculator.countries.USCalculator;
import app.salary.calculator.engine.CalculationOrchestrator;
import app.salary.calculator.engine.CountryCalculator;
import app.salary.calculator.registry.CalculatorRegistry;
import app.salary.calculator.shared.DeductionCalculator;
import app.salary.calculator.shared.IncomeCalculator;
import app.salary.calculator.shared.StudentLoanCalculator;
import app.salary.calculator.shared.TaxBracketCalculator;
import app.salary.rules.RulesRegistry;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import io.javalin.micrometer.MicrometerPlugin;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point for the Salary Calculator API.
 *
 * Manually wires every dependency (no DI framework). All configuration is loaded from
 * environment variables — see {@code Env} below.
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        int port = Env.intValue("SERVER_PORT", 8080);
        String rulePackServiceUrl = Env.stringValue("RULE_PACK_SERVICE_URL", "");

        // ── Shared infra ─────────────────────────────────────────────────────
        ObjectMapper objectMapper = buildObjectMapper();
        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        RequestValidator requestValidator = new RequestValidator();

        // ── Domain components (calculator graph) ─────────────────────────────
        TaxBracketCalculator bracketCalculator = new TaxBracketCalculator();
        DeductionCalculator deductionCalculator = new DeductionCalculator();
        StudentLoanCalculator studentLoanCalculator = new StudentLoanCalculator();
        IncomeCalculator incomeCalculator = new IncomeCalculator();
        log.debug("IncomeCalculator constructed (reserved for future request normalization): {}", incomeCalculator);

        List<CountryCalculator> calculators = List.of(
                new USCalculator(bracketCalculator, deductionCalculator),
                new UKCalculator(bracketCalculator, deductionCalculator, studentLoanCalculator)
        );

        CalculatorRegistry calculatorRegistry = new CalculatorRegistry(calculators);
        RulesRegistry rulesRegistry = new RulesRegistry();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        RulePackClient rulePackClient = new HttpRulePackClient(httpClient, objectMapper, rulePackServiceUrl);

        CalculationOrchestrator orchestrator = new CalculationOrchestrator(
                rulesRegistry, calculatorRegistry, rulePackClient);
        CalculationStore calculationStore = new CalculationStore();

        // ── Javalin app ──────────────────────────────────────────────────────
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(objectMapper, false));
            config.showJavalinBanner = false;
            config.useVirtualThreads = true;
            config.registerPlugin(new MicrometerPlugin(micrometerCfg -> {
                micrometerCfg.registry = meterRegistry;
            }));
        });

        // ── Exception handlers (replace Spring's @ExceptionHandler) ──────────
        app.exception(ValidationException.class, (e, ctx) -> {
            log.warn("Validation failed: {}", e.getErrors());
            ctx.status(HttpStatus.BAD_REQUEST).json(e.getErrors());
        });
        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            log.warn("Illegal argument: {}", e.getMessage());
            ctx.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .json(Map.of("error", String.valueOf(e.getMessage())));
        });
        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unexpected error", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .json(Map.of("error", "Internal server error"));
        });

        // ── Routes ───────────────────────────────────────────────────────────
        new CalculateController(orchestrator, calculatorRegistry, calculationStore, requestValidator).register(app);
        new AppController().register(app);
        new AuthController(requestValidator).register(app);
        new BenefitsController(calculationStore, requestValidator).register(app);
        new InsightsController(calculationStore).register(app);
        new UsersController().register(app);
        new ReportsController(calculationStore).register(app);

        // ── Observability endpoints ──────────────────────────────────────────
        app.get("/actuator/health", ctx -> ctx.json(Map.of("status", "UP")));
        app.get("/actuator/prometheus", ctx ->
                ctx.contentType("text/plain; version=0.0.4").result(meterRegistry.scrape()));

        // ── Boot ─────────────────────────────────────────────────────────────
        app.start(port);
        log.info("Salary Calculator API listening on :{}", port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Salary Calculator API");
            app.stop();
        }, "salary-calculator-shutdown"));
    }

    private static ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        return mapper;
    }

    /** Tiny env-var helper so we don't drag in spring-core just for {@code @Value}. */
    private static final class Env {
        static String stringValue(String key, String defaultValue) {
            String v = System.getenv(key);
            return (v == null || v.isBlank()) ? defaultValue : v;
        }

        static int intValue(String key, int defaultValue) {
            String v = System.getenv(key);
            if (v == null || v.isBlank()) return defaultValue;
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException nfe) {
                return defaultValue;
            }
        }
    }
}
