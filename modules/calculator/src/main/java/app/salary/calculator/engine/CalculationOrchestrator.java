package app.salary.calculator.engine;

import app.salary.calculator.registry.CalculatorRegistry;
import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import app.salary.rules.RulePack;
import app.salary.rules.RulesRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class CalculationOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(CalculationOrchestrator.class);

    private final RulesRegistry rulesRegistry;
    private final CalculatorRegistry calculatorRegistry;

    public CalculationOrchestrator(RulesRegistry rulesRegistry,
                                   CalculatorRegistry calculatorRegistry) {
        this.rulesRegistry = rulesRegistry;
        this.calculatorRegistry = calculatorRegistry;
    }

    public CalculateResponse calculate(CalculateRequest request) {
        String calculationId = "c_" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Starting calculation {} for country {} tax year {}",
                calculationId, request.getCountry(), request.getTaxYear());

        // Load rule pack
        RulePack rulePack = rulesRegistry.getRulePack(
                request.getCountry().name(),
                request.getTaxYear()
        );

        // Get calculator from registry
        CountryCalculator calculator = calculatorRegistry.getCalculator(
                request.getCountry(),
                request.getTaxYear()
        );

        // Convert request to input
        CalculationInput input = CalculationInput.from(request);

        // Perform calculation
        CalculationResult result = calculator.calculate(input, rulePack);

        // Build response
        CalculateResponse response = new CalculateResponse();
        response.setCalculationId(calculationId);
        response.setRulePackVersion(result.getRulePackVersion());
        response.setCurrency(result.getCurrency());

        // Convert to requested cadence
        int periodsPerYear = request.getCadence().getPeriodsPerYear();
        response.setGrossPerCadence(result.getGrossAnnual() / periodsPerYear);
        response.setNetPerCadence(result.getNetAnnual() / periodsPerYear);

        // Adjust line items to cadence
        result.getLineItems().forEach(item -> {
            item.setAmount(item.getAmount() / periodsPerYear);
        });

        response.setLineItems(result.getLineItems());
        response.setExplanation(result.getExplanations());

        log.info("Completed calculation {} - Gross: {}, Net: {}",
                calculationId, response.getGrossPerCadence(), response.getNetPerCadence());

        return response;
    }
}
