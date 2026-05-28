package app.salary.calculator.countries;

import app.salary.calculator.engine.*;
import app.salary.calculator.shared.*;
import app.salary.common.constants.Country;
import app.salary.common.dto.NamedDeduction;
import app.salary.common.dto.Pretax;
import app.salary.rules.RulePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import app.salary.calculator.engine.CountryCalculator;

import java.util.List;

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
        double bonusAnnual = input.getBonusAnnual() != null ? input.getBonusAnnual() : 0.0;
        double baseSalary  = grossAnnual - bonusAnnual;
        result.setGrossAnnual(grossAnnual);
        result.setBaseSalaryAnnual(baseSalary);
        result.setBonusAnnual(bonusAnnual);

        Pretax pretax = input.getPretax();

        // ── Named benefit line items ──────────────────────────────────────────
        if (pretax.getMedical() != null && pretax.getMedical() > 0) {
            result.addLineItem("Medical Premium", pretax.getMedical());
        }
        if (pretax.getDental() != null && pretax.getDental() > 0) {
            result.addLineItem("Dental Premium", pretax.getDental());
        }
        if (pretax.getVision() != null && pretax.getVision() > 0) {
            result.addLineItem("Vision Premium", pretax.getVision());
        }

        // ── 401(k) / pension ─────────────────────────────────────────────────
        double pension401k = deductionCalculator.calculatePensionContribution(pretax, grossAnnual);
        if (pension401k > 0) {
            result.addLineItem("Employee 401(k)", pension401k);
        }

        // ── HSA ───────────────────────────────────────────────────────────────
        if (pretax.getHsa() != null && pretax.getHsa() > 0) {
            result.addLineItem("HSA Contribution", pretax.getHsa());
        }

        // ── Generic pre-tax (percent + fixed catch-all) ───────────────────────
        double genericPretax = deductionCalculator.calculateGenericPretaxDeductions(pretax, grossAnnual);
        if (genericPretax > 0) {
            result.addLineItem("Pre-tax Deductions", genericPretax);
        }

        // ── Named custom deductions ───────────────────────────────────────────
        if (pretax.getCustomDeductions() != null) {
            for (NamedDeduction nd : pretax.getCustomDeductions()) {
                if (nd.getAmount() != null && nd.getAmount() > 0) {
                    result.addLineItem(nd.getName(), nd.getAmount());
                }
            }
        }

        double totalPretaxDeductions = deductionCalculator.calculatePretaxDeductions(pretax, grossAnnual);
        double taxableIncome = grossAnnual - totalPretaxDeductions;

        // ── Federal income tax (base salary portion uses brackets) ────────────
        double federalTax = calculateFederalTax(input, taxableIncome - bonusAnnual, rules);

        // ── Supplemental / bonus withholding at flat 22% ─────────────────────
        double bonusFederalTax = 0.0;
        if (bonusAnnual > 0) {
            bonusFederalTax = bonusAnnual * rules.getFederal().getSupplementalWithholdingRate();
            federalTax += bonusFederalTax;
            result.addExplanation("bonus_withholding",
                    "Bonus of $" + String.format("%.0f", bonusAnnual) +
                    " withheld at flat " +
                    String.format("%.0f", rules.getFederal().getSupplementalWithholdingRate() * 100) +
                    "% supplemental federal rate");
        }
        result.addLineItem("Federal Income Tax", federalTax);
        result.addExplanation("fed_tax_brackets",
                "Applied 2025 federal tax brackets based on " +
                        input.getUsOptions().getFilingStatus());

        // ── State income tax ──────────────────────────────────────────────────
        double stateTax = calculateStateTax(input, taxableIncome, rules);
        if (stateTax > 0) {
            result.addLineItem("State Income Tax", stateTax);
            result.addExplanation("state_tax",
                    "Applied " + input.getUsOptions().getState() + " state tax rates");
        }

        // ── FICA ──────────────────────────────────────────────────────────────
        double socialSecurity = calculateSocialSecurity(grossAnnual, rules);
        result.addLineItem("FICA (Social Security)", socialSecurity);

        double medicare = calculateMedicare(grossAnnual, rules);
        result.addLineItem("Medicare", medicare);

        if (grossAnnual > rules.getFica().getAdditionalMedicareThreshold()) {
            result.addExplanation("additional_medicare",
                    "Additional Medicare tax applied for income over $" +
                            String.format("%.0f", rules.getFica().getAdditionalMedicareThreshold()));
        }

        // ── Post-tax deductions ───────────────────────────────────────────────
        double posttaxDeductions = deductionCalculator.calculatePosttaxDeductions(input.getPosttax());
        if (posttaxDeductions > 0) {
            result.addLineItem("Post-tax Deductions", posttaxDeductions);
        }

        double netAnnual = grossAnnual - totalPretaxDeductions - federalTax - stateTax
                - socialSecurity - medicare - posttaxDeductions;
        result.setNetAnnual(netAnnual);

        return result;
    }

    private double calculateFederalTax(CalculationInput input, double taxableBaseSalary, RulePack rules) {
        String filingStatus = input.getUsOptions().getFilingStatus().name();
        Double standardDeductionObj = rules.getFederal().getStandardDeductions().get(filingStatus);
        double standardDeduction = standardDeductionObj != null ? standardDeductionObj : 14600.0;
        double adjustedIncome = Math.max(0, taxableBaseSalary - standardDeduction);
        List<RulePack.TaxBracket> brackets = rules.getFederal().getBracketsForFilingStatus(filingStatus);
        return bracketCalculator.calculateTax(adjustedIncome, brackets);
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
