package app.salary.api.controller;

import app.salary.api.validation.RequestValidator;
import app.salary.calculator.engine.CalculationOrchestrator;
import app.salary.calculator.registry.CalculatorRegistry;
import app.salary.common.constants.Country;
import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CalculateController {
    private static final Logger log = LoggerFactory.getLogger(CalculateController.class);

    private final CalculationOrchestrator orchestrator;
    private final CalculatorRegistry calculatorRegistry;
    private final RequestValidator validator;

    public CalculateController(CalculationOrchestrator orchestrator,
                               CalculatorRegistry calculatorRegistry,
                               RequestValidator validator) {
        this.orchestrator = orchestrator;
        this.calculatorRegistry = calculatorRegistry;
        this.validator = validator;
    }

    public void register(RoutesConfig routes) {
        routes.post("/v1/calculate", this::calculate);
        routes.get("/v1/countries", this::getSupportedCountries);
        routes.get("/v1/countries/US/states", this::getUsStates);
        routes.get("/v1/tax-years", this::getSupportedTaxYears);
    }

    private void calculate(Context ctx) {
        CalculateRequest request = ctx.bodyAsClass(CalculateRequest.class);
        validator.validate(request);

        if (request.getCountry() != null) MDC.put("country", String.valueOf(request.getCountry()));
        if (request.getCountryOptions() != null
                && request.getCountryOptions().getUs() != null
                && request.getCountryOptions().getUs().getState() != null) {
            MDC.put("state", request.getCountryOptions().getUs().getState());
        }

        boolean hasBonus  = request.getBonus() != null && request.getBonus() > 0;
        boolean hasHourly = request.getEarnings() != null && request.getEarnings().getHourly() != null;
        log.info("calculate: country={} taxYear={} cadence={} hasBonus={} hasHourly={}",
                request.getCountry(), request.getTaxYear(), request.getCadence(), hasBonus, hasHourly);

        CalculateResponse response = orchestrator.calculate(request);

        int lineItemCount = response.getLineItems() != null ? response.getLineItems().size() : 0;
        log.info("calculate done: lineItems={}", lineItemCount);

        ctx.json(response);
    }

    private void getSupportedCountries(Context ctx) {
        List<Country> countries = calculatorRegistry.getSupportedCountries();
        ctx.json(Map.of(
                "countries", countries,
                "count", countries.size()
        ));
    }

    private void getUsStates(Context ctx) {
        List<Map<String, String>> states = List.of(
                stateEntry("AL", "Alabama"), stateEntry("AK", "Alaska"),
                stateEntry("AZ", "Arizona"), stateEntry("AR", "Arkansas"),
                stateEntry("CA", "California"), stateEntry("CO", "Colorado"),
                stateEntry("CT", "Connecticut"), stateEntry("DE", "Delaware"),
                stateEntry("FL", "Florida"), stateEntry("GA", "Georgia"),
                stateEntry("HI", "Hawaii"), stateEntry("ID", "Idaho"),
                stateEntry("IL", "Illinois"), stateEntry("IN", "Indiana"),
                stateEntry("IA", "Iowa"), stateEntry("KS", "Kansas"),
                stateEntry("KY", "Kentucky"), stateEntry("LA", "Louisiana"),
                stateEntry("ME", "Maine"), stateEntry("MD", "Maryland"),
                stateEntry("MA", "Massachusetts"), stateEntry("MI", "Michigan"),
                stateEntry("MN", "Minnesota"), stateEntry("MS", "Mississippi"),
                stateEntry("MO", "Missouri"), stateEntry("MT", "Montana"),
                stateEntry("NE", "Nebraska"), stateEntry("NV", "Nevada"),
                stateEntry("NH", "New Hampshire"), stateEntry("NJ", "New Jersey"),
                stateEntry("NM", "New Mexico"), stateEntry("NY", "New York"),
                stateEntry("NC", "North Carolina"), stateEntry("ND", "North Dakota"),
                stateEntry("OH", "Ohio"), stateEntry("OK", "Oklahoma"),
                stateEntry("OR", "Oregon"), stateEntry("PA", "Pennsylvania"),
                stateEntry("RI", "Rhode Island"), stateEntry("SC", "South Carolina"),
                stateEntry("SD", "South Dakota"), stateEntry("TN", "Tennessee"),
                stateEntry("TX", "Texas"), stateEntry("UT", "Utah"),
                stateEntry("VT", "Vermont"), stateEntry("VA", "Virginia"),
                stateEntry("WA", "Washington"), stateEntry("WV", "West Virginia"),
                stateEntry("WI", "Wisconsin"), stateEntry("WY", "Wyoming"),
                stateEntry("DC", "District of Columbia")
        );
        ctx.json(states);
    }

    private void getSupportedTaxYears(Context ctx) {
        String country = ctx.queryParamAsClass("country", String.class).getOrDefault("US");
        List<Integer> taxYears = List.of(2025);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("country", country.toUpperCase());
        response.put("supportedTaxYears", taxYears);
        response.put("defaultTaxYear", taxYears.getFirst());
        ctx.json(response);
    }

    private Map<String, String> stateEntry(String code, String name) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        return m;
    }
}
