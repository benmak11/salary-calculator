package app.salary.api.controller;

import app.salary.api.service.CalculationStore;
import app.salary.calculator.engine.CalculationOrchestrator;
import app.salary.calculator.registry.CalculatorRegistry;
import app.salary.common.constants.Country;
import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
@Tag(name = "Calculation", description = "Salary calculation endpoints")
public class CalculateController {
    private static final Logger log = LoggerFactory.getLogger(CalculateController.class);

    private final CalculationOrchestrator orchestrator;
    private final CalculatorRegistry calculatorRegistry;
    private final CalculationStore calculationStore;

    public CalculateController(CalculationOrchestrator orchestrator,
                               CalculatorRegistry calculatorRegistry,
                               CalculationStore calculationStore) {
        this.orchestrator = orchestrator;
        this.calculatorRegistry = calculatorRegistry;
        this.calculationStore = calculationStore;
    }

    @PostMapping("/calculate")
    @Operation(summary = "Calculate net pay for a given country and tax year")
    public ResponseEntity<CalculateResponse> calculate(@Valid @RequestBody CalculateRequest request) {
        log.info("Received calculation request for country: {}, taxYear: {}",
                request.getCountry(), request.getTaxYear());

        try {
            CalculateResponse response = orchestrator.calculate(request);
            calculationStore.store(response);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid calculation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error processing calculation", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/countries")
    @Operation(summary = "List all supported countries")
    public ResponseEntity<Map<String, Object>> getSupportedCountries() {
        List<Country> countries = calculatorRegistry.getSupportedCountries();
        return ResponseEntity.ok(Map.of(
                "countries", countries,
                "count", countries.size()
        ));
    }

    @GetMapping("/countries/US/states")
    @Operation(summary = "List all supported US states with their two-letter codes")
    public ResponseEntity<List<Map<String, String>>> getUsStates() {
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
        return ResponseEntity.ok(states);
    }

    @GetMapping("/tax-years")
    @Operation(summary = "List supported tax years for a given country")
    public ResponseEntity<Map<String, Object>> getSupportedTaxYears(
            @RequestParam(defaultValue = "US") String country) {
        List<Integer> taxYears = List.of(2025);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("country", country.toUpperCase());
        response.put("supportedTaxYears", taxYears);
        response.put("defaultTaxYear", taxYears.get(taxYears.size() - 1));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    @Operation(summary = "Health check endpoint")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "calculators", calculatorRegistry.getCalculatorCount(),
                "supportedCountries", calculatorRegistry.getSupportedCountries().size()
        ));
    }

    private Map<String, String> stateEntry(String code, String name) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        return m;
    }
}
