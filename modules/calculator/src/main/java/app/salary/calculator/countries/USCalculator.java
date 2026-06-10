package app.salary.calculator.countries;

import app.salary.calculator.engine.*;
import app.salary.calculator.shared.*;
import app.salary.common.constants.Country;
import app.salary.common.dto.LineItemCategory;
import app.salary.common.dto.NamedDeduction;
import app.salary.common.dto.Pretax;
import app.salary.common.dto.W4;
import app.salary.rules.RulePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class USCalculator implements CountryCalculator {
    private static final Logger log = LoggerFactory.getLogger(USCalculator.class);

    private final TaxBracketCalculator bracketCalculator;
    private final DeductionCalculator deductionCalculator;

    public USCalculator(TaxBracketCalculator bracketCalculator,
                        DeductionCalculator deductionCalculator) {
        this.bracketCalculator = bracketCalculator;
        this.deductionCalculator = deductionCalculator;
    }

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
        double supplementalAnnual = input.getSupplementalAnnual();
        double regularWages = input.getRegularWagesAnnual();
        result.setGrossAnnual(grossAnnual);
        result.setBaseSalaryAnnual(grossAnnual - supplementalAnnual);
        result.setBonusAnnual(supplementalAnnual);

        W4 w4 = w4OrDefault(input);
        Pretax pretax = input.getPretax();

        // ── Earnings line items (positive, displayed by iOS right-rail) ───────
        if (input.getSalaryAnnual() != null && input.getSalaryAnnual() > 0) {
            result.addLineItem("Salary", input.getSalaryAnnual(), LineItemCategory.EARNINGS);
        }
        if (input.getOvertimeAnnual() != null && input.getOvertimeAnnual() > 0) {
            result.addLineItem("Overtime", input.getOvertimeAnnual(), LineItemCategory.EARNINGS);
        }
        if (input.getDoubleTimeAnnual() != null && input.getDoubleTimeAnnual() > 0) {
            result.addLineItem("Double Time", input.getDoubleTimeAnnual(), LineItemCategory.EARNINGS);
        }
        if (input.getBonusAnnual() != null && input.getBonusAnnual() > 0) {
            result.addLineItem("Bonus", input.getBonusAnnual(), LineItemCategory.EARNINGS);
        }
        if (input.getCommissionAnnual() != null && input.getCommissionAnnual() > 0) {
            result.addLineItem("Commission", input.getCommissionAnnual(), LineItemCategory.EARNINGS);
        }

        // ── Named pre-tax benefit line items ──────────────────────────────────
        if (pretax.getMedical() != null && pretax.getMedical() > 0) {
            result.addLineItem("Medical Premium", pretax.getMedical(), LineItemCategory.PRE_TAX_BENEFIT);
        }
        if (pretax.getDental() != null && pretax.getDental() > 0) {
            result.addLineItem("Dental Premium", pretax.getDental(), LineItemCategory.PRE_TAX_BENEFIT);
        }
        if (pretax.getVision() != null && pretax.getVision() > 0) {
            result.addLineItem("Vision Premium", pretax.getVision(), LineItemCategory.PRE_TAX_BENEFIT);
        }
        if (pretax.getHealthcareFsa() != null && pretax.getHealthcareFsa() > 0) {
            result.addLineItem("Healthcare FSA", pretax.getHealthcareFsa(), LineItemCategory.PRE_TAX_BENEFIT);
        }
        if (pretax.getDependentCareFsa() != null && pretax.getDependentCareFsa() > 0) {
            result.addLineItem("Dependent Care FSA", pretax.getDependentCareFsa(), LineItemCategory.PRE_TAX_BENEFIT);
        }
        if (pretax.getHsa() != null && pretax.getHsa() > 0) {
            result.addLineItem("HSA Contribution", pretax.getHsa(), LineItemCategory.PRE_TAX_BENEFIT);
        }

        // ── 401(k) / pension (Traditional — pre-tax retirement) ──────────────
        double pension401k = deductionCalculator.calculatePensionContribution(pretax, grossAnnual);
        if (pension401k > 0) {
            result.addLineItem("401(k)", pension401k, LineItemCategory.RETIREMENT);
        }

        // ── Generic pre-tax (percent + fixed catch-all) ───────────────────────
        double genericPretax = deductionCalculator.calculateGenericPretaxDeductions(pretax, grossAnnual);
        // genericPretax already counts hsa; back it out so we don't double-show it
        double hsa = pretax.getHsa() != null ? pretax.getHsa() : 0.0;
        double genericMinusHsa = genericPretax - hsa;
        if (genericMinusHsa > 0) {
            result.addLineItem("Pre-tax Deductions", genericMinusHsa, LineItemCategory.PRE_TAX_BENEFIT);
        }

        // ── Named custom pre-tax deductions ───────────────────────────────────
        if (pretax.getCustomDeductions() != null) {
            for (NamedDeduction nd : pretax.getCustomDeductions()) {
                if (nd.getAmount() != null && nd.getAmount() > 0) {
                    result.addLineItem(nd.getName(), nd.getAmount(), LineItemCategory.PRE_TAX_BENEFIT);
                }
            }
        }

        double totalPretaxDeductions = deductionCalculator.calculatePretaxDeductions(pretax, grossAnnual);

        // ── Federal income tax ────────────────────────────────────────────────
        double federalTax = 0.0;
        double bonusFederalTax;
        if (!Boolean.TRUE.equals(w4.getExemptFederal())) {
            federalTax = calculateFederalRegular(input, regularWages, totalPretaxDeductions, w4, rules);
            // W-4 step 3: dependents credit reduces withholding (modern W-4 only)
            if (!Boolean.TRUE.equals(w4.getUseOldW4())) {
                double dependents = w4.getDependentsAmount() != null ? w4.getDependentsAmount() : 0.0;
                federalTax = Math.max(0, federalTax - dependents);
            }
            // W-4 step 4(c): additional withholding per pay period
            double additional = w4.getAdditionalWithholding() != null ? w4.getAdditionalWithholding() : 0.0;
            if (additional > 0) {
                federalTax += additional * input.getPayCadence().getPeriodsPerYear();
            }
            // Supplemental withholding on bonus + commission
            if (supplementalAnnual > 0) {
                bonusFederalTax = supplementalAnnual * rules.getFederal().getSupplementalWithholdingRate();
                federalTax += bonusFederalTax;
                result.addExplanation("supplemental_withholding",
                        "Supplemental wages of $" + String.format("%.0f", supplementalAnnual) +
                        " withheld at flat " +
                        String.format("%.0f", rules.getFederal().getSupplementalWithholdingRate() * 100) +
                        "% federal rate");
            }
            result.addExplanation("fed_tax_brackets",
                    "Applied 2025 federal tax brackets based on " +
                            input.getUsOptions().getFilingStatus());
        } else {
            result.addExplanation("fed_tax_exempt", "Filer exempt from federal income tax withholding");
        }
        if (federalTax > 0) {
            result.addLineItem("Federal Income Tax", federalTax, LineItemCategory.TAX_FEDERAL);
        }

        // ── State income tax ──────────────────────────────────────────────────
        double stateTaxableIncome = grossAnnual - totalPretaxDeductions;
        double stateTax = calculateStateTax(input, stateTaxableIncome, rules);
        if (stateTax > 0) {
            String stateCode = input.getUsOptions().getState();
            result.addLineItem(stateCode + " State Income Tax", stateTax, LineItemCategory.TAX_STATE);
            result.addExplanation("state_tax",
                    "Applied " + stateCode + " state tax rates");
        }

        // ── FICA ──────────────────────────────────────────────────────────────
        double socialSecurity = 0.0;
        if (!Boolean.TRUE.equals(w4.getExemptSocialSecurity())) {
            socialSecurity = calculateSocialSecurity(grossAnnual, rules);
            result.addLineItem("Social Security", socialSecurity, LineItemCategory.TAX_FICA);
        } else {
            result.addExplanation("ss_exempt", "Filer exempt from Social Security tax");
        }

        double medicare = 0.0;
        if (!Boolean.TRUE.equals(w4.getExemptMedicare())) {
            medicare = calculateMedicare(grossAnnual, rules);
            result.addLineItem("Medicare", medicare, LineItemCategory.TAX_FICA);
            if (grossAnnual > rules.getFica().getAdditionalMedicareThreshold()) {
                result.addExplanation("additional_medicare",
                        "Additional Medicare tax applied for income over $" +
                                String.format("%.0f", rules.getFica().getAdditionalMedicareThreshold()));
            }
        } else {
            result.addExplanation("medicare_exempt", "Filer exempt from Medicare tax");
        }

        // ── Post-tax deductions (fixed catch-all) ─────────────────────────────
        double posttaxFixed = deductionCalculator.calculatePosttaxDeductions(input.getPosttax());
        if (posttaxFixed > 0) {
            result.addLineItem("Post-tax Deductions", posttaxFixed, LineItemCategory.POST_TAX);
        }

        // ── Roth 401(k) — post-tax retirement, regular wages only ─────────────
        double roth401k = deductionCalculator.calculateRoth401k(input.getPosttax(), regularWages);
        if (roth401k > 0) {
            result.addLineItem("Roth 401(k)", roth401k, LineItemCategory.RETIREMENT);
        }

        double netAnnual = grossAnnual
                - totalPretaxDeductions
                - federalTax
                - stateTax
                - socialSecurity
                - medicare
                - posttaxFixed
                - roth401k;
        result.setNetAnnual(netAnnual);

        return result;
    }

    private W4 w4OrDefault(CalculationInput input) {
        if (input.getUsOptions() != null && input.getUsOptions().getW4() != null) {
            return input.getUsOptions().getW4();
        }
        return new W4();
    }

    private double calculateFederalRegular(CalculationInput input,
                                           double regularWages,
                                           double totalPretaxDeductions,
                                           W4 w4,
                                           RulePack rules) {
        String filingStatus = input.getUsOptions().getFilingStatus().name();
        List<RulePack.TaxBracket> brackets = rules.getFederal().getBracketsForFilingStatus(filingStatus);

        // Pre-2020 W-4: withholding is allowance-based. Each allowance shelters a
        // fixed dollar amount; the modern step-3/4 fields don't exist on that form.
        if (Boolean.TRUE.equals(w4.getUseOldW4())) {
            int allowances = input.getUsOptions().getAllowances() != null
                    ? input.getUsOptions().getAllowances() : 0;
            double allowanceValue = rules.getFederal().getWithholdingAllowance();
            double legacyTaxable = Math.max(0,
                    regularWages - totalPretaxDeductions - (allowances * allowanceValue));
            return bracketCalculator.calculateTax(legacyTaxable, brackets);
        }

        Double standardDeductionObj = rules.getFederal().getStandardDeductions().get(filingStatus);
        double standardDeduction = standardDeductionObj != null ? standardDeductionObj : 14600.0;
        double itemized = w4.getItemizedDeductions() != null ? w4.getItemizedDeductions() : 0.0;
        double effectiveDeduction = Math.max(itemized, standardDeduction);
        double otherIncome = w4.getOtherIncome() != null ? w4.getOtherIncome() : 0.0;
        double taxable = Math.max(0,
                regularWages - totalPretaxDeductions + otherIncome - effectiveDeduction);
        return bracketCalculator.calculateTax(taxable, brackets);
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
