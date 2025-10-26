package app.salary.api.controller;

import app.salary.calculator.engine.CalculationOrchestrator;
import app.salary.calculator.registry.CalculatorRegistry;
import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import app.salary.common.constants.Country;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
@Tag(name = "Calculation", description = "Salary calculation endpoints")
public class CalculateController {
    private static final Logger log = LoggerFactory.getLogger(CalculateController.class);

    private final CalculationOrchestrator orchestrator;
    private final CalculatorRegistry calculatorRegistry;

    public CalculateController(CalculationOrchestrator orchestrator,
                               CalculatorRegistry calculatorRegistry) {
        this.orchestrator = orchestrator;
        this.calculatorRegistry = calculatorRegistry;
    }

    @PostMapping("/calculate")
    @Operation(summary = "Calculate net pay for a given country and tax year")
    public ResponseEntity<CalculateResponse> calculate(@Valid @RequestBody CalculateRequest request) {
        log.info("Received calculation request for country: {}, taxYear: {}",
                request.getCountry(), request.getTaxYear());

        try {
            CalculateResponse response = orchestrator.calculate(request);
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

    @GetMapping("/health")
    @Operation(summary = "Health check endpoint")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "calculators", calculatorRegistry.getCalculatorCount(),
                "supportedCountries", calculatorRegistry.getSupportedCountries().size()
        ));
    }
}
