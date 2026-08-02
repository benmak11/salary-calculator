package app.salary.calculator.engine;

import app.salary.calculator.client.RulePackClient;
import app.salary.calculator.registry.CalculatorRegistry;
import app.salary.common.constants.ApiConstants;
import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import app.salary.rules.RulePack;
import app.salary.rules.RulesRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class CalculationOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(CalculationOrchestrator.class);

    private static final String METRIC_RULEPACK_FETCH = "salary.rulepack.fetch";
    private static final String METRIC_CALCULATION   = "salary.calculation";

    private final RulesRegistry rulesRegistry;
    private final CalculatorRegistry calculatorRegistry;
    private final RulePackClient rulePackClient;
    private final MeterRegistry meterRegistry;

    public CalculationOrchestrator(RulesRegistry rulesRegistry,
                                   CalculatorRegistry calculatorRegistry,
                                   RulePackClient rulePackClient,
                                   MeterRegistry meterRegistry) {
        this.rulesRegistry = rulesRegistry;
        this.calculatorRegistry = calculatorRegistry;
        this.rulePackClient = rulePackClient;
        this.meterRegistry = meterRegistry;
    }

    public CalculateResponse calculate(CalculateRequest request) {
        String calculationId = "c_" + UUID.randomUUID().toString().substring(0, 8);
        String country = request.getCountry().name();
        log.info("Starting calculation {} for country {} tax year {}",
                calculationId, request.getCountry(), request.getTaxYear());

        // Load rule pack: try rule-pack-service first, fall back to embedded classpath JSON
        Timer.Sample rulePackSample = Timer.start(meterRegistry);
        RulePack rulePack = null;
        if (rulePackClient != null) {
            rulePack = rulePackClient.fetchLatest(country, request.getTaxYear()).orElse(null);
            if (rulePack != null) {
                log.debug("Loaded rule pack from rule-pack-service for {} {}", request.getCountry(), request.getTaxYear());
            }
        }
        String rulePackSource;
        if (rulePack == null) {
            log.info("Falling back to classpath rule pack for {} {}", request.getCountry(), request.getTaxYear());
            rulePack = rulesRegistry.getRulePack(country, request.getTaxYear());
            rulePackSource = "classpath";
        } else {
            rulePackSource = "remote";
        }
        rulePackSample.stop(meterRegistry.timer(METRIC_RULEPACK_FETCH,
                "source", rulePackSource, ApiConstants.COUNTRY, country));

        // Get calculator from registry
        CountryCalculator calculator = calculatorRegistry.getCalculator(
                request.getCountry(),
                request.getTaxYear()
        );

        // Convert request to input
        CalculationInput input = CalculationInput.from(request);

        // Perform calculation
        Timer.Sample calcSample = Timer.start(meterRegistry);
        CalculationResult result = calculator.calculate(input, rulePack);
        calcSample.stop(meterRegistry.timer(METRIC_CALCULATION, ApiConstants.COUNTRY, country));

        // Build response
        CalculateResponse response = new CalculateResponse();
        response.setCalculationId(calculationId);
        response.setRulePackVersion(result.getRulePackVersion());
        response.setCurrency(result.getCurrency());

        // Convert to requested cadence. grossPerCadence/netPerCadence are the
        // regular-wages-only (salary+OT+DT) headline — bonus/commission/RSU
        // are lump-sum, not recurring per paycheck, so they're excluded here
        // and represented annually via `supplemental` instead. Falls back to
        // the blended annual fields when a calculator (e.g. UK, which has no
        // supplemental-income concept) never sets the regular-only ones.
        int periodsPerYear = request.getCadence().getPeriodsPerYear();
        double perCadenceGrossAnnual = result.getRegularGrossAnnual() != null
                ? result.getRegularGrossAnnual() : result.getGrossAnnual();
        double perCadenceNetAnnual = result.getRegularNetAnnual() != null
                ? result.getRegularNetAnnual() : result.getNetAnnual();
        response.setGrossPerCadence(perCadenceGrossAnnual / periodsPerYear);
        response.setNetPerCadence(perCadenceNetAnnual / periodsPerYear);

        double baseSalary = result.getBaseSalaryAnnual() != null
                ? result.getBaseSalaryAnnual()
                : result.getGrossAnnual();
        response.setBaseSalaryPerCadence(baseSalary / periodsPerYear);
        // Bonus/commission/RSU are lump-sum, not per-cadence — see `supplemental`.
        response.setBonusPerCadence(0.0);

        // Supplemental slice stays annual (lump-sum-shaped) — see SupplementalBreakdown javadoc
        response.setSupplemental(result.getSupplemental());

        // Adjust line items to cadence
        result.getLineItems().forEach(item -> item.setAmount(item.getAmount() / periodsPerYear));

        response.setLineItems(result.getLineItems());
        response.setExplanation(result.getExplanations());

        int lineItemCount = response.getLineItems() != null ? response.getLineItems().size() : 0;
        log.info("Completed calculation {} - lineItems={}", calculationId, lineItemCount);

        return response;
    }
}
