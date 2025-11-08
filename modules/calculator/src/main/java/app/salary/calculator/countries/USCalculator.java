package app.salary.calculator.countries;

import app.salary.calculator.engine.*;
import app.salary.calculator.shared.*;
import app.salary.common.constants.Country;
import app.salary.rules.RulePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import app.salary.calculator.engine.CountryCalculator;

@Component
public class USCalculator implements CountryCalculator {
    private static final Logger log = LoggerFactory.getLogger(USCalculator.class);

    @Autowired
    private TaxBracketCalculator bracketCalculator;

    @Autowired
    private DeductionCalculator deductionCalculator;

    @Override
    public boolean supports(Country country, int taxYear) {
        return country == Country.US && taxYear >= 2025;
    }

    @Override
    public CalculationResult calculate(CalculationInput input, RulePack rules) {
        CalculationResult result = new CalculationResult();
        result.setCurrency("USD");
        result.setRulePackVersion(rules.getMetadata().getVersion());

        double grossAnnual = input.getAnnualGross();
        result.setGrossAnnual(grossAnnual);

        double pretaxDeductions = deductionCalculator.calculatePretaxDeductions(
                input.getPretax(), grossAnnual);
        result.addLineItem("Pre-tax Deductions", pretaxDeductions);

        double taxableIncome = grossAnnual - pretaxDeductions;

        double federalTax = calculateFederalTax(input, taxableIncome, rules);
        result.addLineItem("Federal Income Tax", federalTax);
        result.addExplanation("fed_tax_brackets",
                "Applied 2025 federal tax brackets based on " +
                        input.getUsOptions().getFilingStatus());

        double stateTax = calculateStateTax(input, taxableIncome, rules);
        if (stateTax > 0) {
            result.addLineItem("State Income Tax", stateTax);
            result.addExplanation("state_tax",
                    "Applied " + input.getUsOptions().getState() + " state tax rates");
        }

        double socialSecurity = calculateSocialSecurity(grossAnnual, rules);
        result.addLineItem("FICA (Social Security)", socialSecurity);

        double medicare = calculateMedicare(grossAnnual, rules);
        result.addLineItem("Medicare", medicare);

        if (grossAnnual > rules.getFica().getAdditionalMedicareThreshold()) {
            result.addExplanation("additional_medicare",
                    "Additional Medicare tax applied for income over $" +
                            String.format("%.0f", rules.getFica().getAdditionalMedicareThreshold()));
        }

        double posttaxDeductions = deductionCalculator.calculatePosttaxDeductions(
                input.getPosttax());
        if (posttaxDeductions > 0) {
            result.addLineItem("Post-tax Deductions", posttaxDeductions);
        }

        double netAnnual = grossAnnual - pretaxDeductions - federalTax - stateTax
                - socialSecurity - medicare - posttaxDeductions;
        result.setNetAnnual(netAnnual);

        return result;
    }

    private double calculateFederalTax(CalculationInput input, double taxableIncome, RulePack rules) {
        String filingStatus = input.getUsOptions().getFilingStatus().name();
        double standardDeduction = rules.getFederal().getStandardDeductions().get(filingStatus);
        double adjustedIncome = Math.max(0, taxableIncome - standardDeduction);
        return bracketCalculator.calculateTax(adjustedIncome, rules.getFederal().getBrackets());
    }

    private double calculateStateTax(CalculationInput input, double taxableIncome, RulePack rules) {
        String state = input.getUsOptions().getState();
        RulePack.StateRules stateRules = rules.getStates().get(state);

        if (stateRules == null) {
            log.warn("No state rules found for: {}", state);
            return 0.0;
        }

        double stateTax = bracketCalculator.calculateTax(taxableIncome, stateRules.getBrackets());
        if (stateRules.getLocal() != null && stateRules.getLocal() > 0) {
            stateTax += taxableIncome * stateRules.getLocal();
        }
        return stateTax;
    }

    private double calculateSocialSecurity(double grossAnnual, RulePack rules) {
        double ssWageBase = rules.getFica().getSsWageBase();
        double taxableWages = Math.min(grossAnnual, ssWageBase);
        return taxableWages * rules.getFica().getSsRate();
    }

    private double calculateMedicare(double grossAnnual, RulePack rules) {
        double medicare = grossAnnual * rules.getFica().getMedicareRate();
        if (grossAnnual > rules.getFica().getAdditionalMedicareThreshold()) {
            double additionalAmount = grossAnnual - rules.getFica().getAdditionalMedicareThreshold();
            medicare += additionalAmount * rules.getFica().getAdditionalRate();
        }
        return medicare;
    }
}
