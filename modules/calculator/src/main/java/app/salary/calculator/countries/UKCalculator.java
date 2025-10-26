package app.salary.calculator.countries;

import app.salary.calculator.engine.*;
import app.salary.calculator.shared.*;
import app.salary.common.constants.Country;
import app.salary.rules.RulePack;
import org.springframework.beans.factory.annotation.Autowired;
import app.salary.calculator.engine.CountryCalculator;
import org.springframework.stereotype.Component;

@Component
public class UKCalculator implements CountryCalculator {
    @Autowired
    private TaxBracketCalculator bracketCalculator;

    @Autowired
    private DeductionCalculator deductionCalculator;

    @Autowired
    private IncomeCalculator incomeCalculator;

    @Autowired
    private StudentLoanCalculator studentLoanCalculator;

    @Override
    public boolean supports(Country country, int taxYear) {
        return country == Country.UK && taxYear >= 2025;
    }

    @Override
    public CalculationResult calculate(CalculationInput input, RulePack rules) {
        CalculationResult result = new CalculationResult();
        result.setCurrency("GBP");
        result.setRulePackVersion(rules.getMetadata().getVersion());

        double grossAnnual = incomeCalculator.calculateAnnualGross(input.getIncome(), 37.5);
        result.setGrossAnnual(grossAnnual);

        double pensionContribution = deductionCalculator.calculatePensionContribution(
                input.getPretax(), grossAnnual);
        double employerPensionMinimum = grossAnnual * 0.03;

        double taxableIncome = grossAnnual - pensionContribution;
        double personalAllowance = calculatePersonalAllowance(taxableIncome, rules);
        double taxableAfterAllowance = Math.max(0, taxableIncome - personalAllowance);

        TaxBracketCalculator.TaxBreakdown incomeTaxBreakdown =
                bracketCalculator.calculateTaxWithBreakdown(taxableAfterAllowance,
                        rules.getIncomeTax().getBands());

        NIBreakdown niBreakdown = calculateNationalInsuranceByBands(taxableIncome, rules);

        double studentLoan = studentLoanCalculator.calculateRepayment(
                input.getPosttax().getStudentLoanPlan(), taxableIncome, rules);

        double posttaxDeductions = deductionCalculator.calculatePosttaxDeductions(input.getPosttax());

        // Build detailed line items
        result.addLineItem("Gross Salary", grossAnnual);
        result.addLineItem("Tax-Free Allowance", -personalAllowance);
        result.addLineItem("Taxable Income", taxableAfterAllowance);

        var bands = incomeTaxBreakdown.getBands();
        if (bands.containsKey(0)) {
            var band = bands.get(0);
            result.addLineItem("Income Tax (Basic Rate 20%)", band.getTax());
            result.addExplanation("basic_rate_tax",
                    String.format("Basic rate (20%%) on £%.2f", band.getIncome()));
        }

        if (bands.containsKey(1)) {
            var band = bands.get(1);
            result.addLineItem("Income Tax (Higher Rate 40%)", band.getTax());
            result.addExplanation("higher_rate_tax",
                    String.format("Higher rate (40%%) on £%.2f", band.getIncome()));
        }

        if (bands.containsKey(2)) {
            var band = bands.get(2);
            result.addLineItem("Income Tax (Additional Rate 45%)", band.getTax());
            result.addExplanation("additional_rate_tax",
                    String.format("Additional rate (45%%) on £%.2f", band.getIncome()));
        }

        result.addLineItem("Total Income Tax", incomeTaxBreakdown.getTotalTax());

        if (niBreakdown.mainRateNI > 0) {
            result.addLineItem("National Insurance (Main Rate 8%)", niBreakdown.mainRateNI);
            result.addExplanation("ni_main_rate",
                    String.format("8%% rate on £%.2f (between £%.0f and £%.0f)",
                            niBreakdown.mainRateIncome,
                            rules.getNi().getPrimaryThresholdAnnual(),
                            rules.getNi().getUpperEarningsLimit()));
        }

        if (niBreakdown.upperRateNI > 0) {
            result.addLineItem("National Insurance (Upper Rate 2%)", niBreakdown.upperRateNI);
            result.addExplanation("ni_upper_rate",
                    String.format("2%% rate on £%.2f (above £%.0f)",
                            niBreakdown.upperRateIncome,
                            rules.getNi().getUpperEarningsLimit()));
        }

        result.addLineItem("Total National Insurance", niBreakdown.totalNI);

        if (pensionContribution > 0) {
            result.addLineItem("Employee Pension Contribution", pensionContribution);
            double pensionPercent = input.getPretax().getPensionPercent() * 100;
            result.addExplanation("pension_contribution",
                    String.format("Employee contribution: %.1f%% of gross salary (£%.2f). " +
                                    "Employer minimum contribution: 3%% (£%.2f)",
                            pensionPercent, pensionContribution, employerPensionMinimum));
        }

        if (studentLoan > 0) {
            String planName = input.getPosttax().getStudentLoanPlan() != null
                    ? input.getPosttax().getStudentLoanPlan().name() : "Plan 2";
            result.addLineItem("Student Loan (" + planName + ")", studentLoan);
        }

        if (posttaxDeductions > 0) {
            result.addLineItem("Other Post-tax Deductions", posttaxDeductions);
        }

        double netAnnual = grossAnnual - incomeTaxBreakdown.getTotalTax() - niBreakdown.totalNI
                - pensionContribution - studentLoan - posttaxDeductions;
        result.setNetAnnual(netAnnual);
        result.addLineItem("Net Take-Home Pay", netAnnual);

        if (taxableIncome > rules.getIncomeTax().getTaperStart()) {
            result.addExplanation("personal_allowance_taper",
                    "Personal allowance reduced due to income over £" +
                            String.format("%.0f", rules.getIncomeTax().getTaperStart()));
        } else {
            result.addExplanation("personal_allowance",
                    String.format("Full personal allowance of £%.0f applied", personalAllowance));
        }

        String taxCode = input.getUkOptions() != null ? input.getUkOptions().getTaxCode() : "1257L";
        result.addExplanation("tax_code", String.format("Tax code %s used for calculation", taxCode));

        return result;
    }

    private double calculatePersonalAllowance(double taxableIncome, RulePack rules) {
        RulePack.IncomeTax incomeTax = rules.getIncomeTax();
        double personalAllowance = incomeTax.getPersonalAllowance();

        if (taxableIncome > incomeTax.getTaperStart()) {
            double excessIncome = taxableIncome - incomeTax.getTaperStart();
            double reduction = excessIncome * incomeTax.getTaperRate();
            personalAllowance = Math.max(0, personalAllowance - reduction);
        }

        return personalAllowance;
    }

    private NIBreakdown calculateNationalInsuranceByBands(double taxableIncome, RulePack rules) {
        NIBreakdown breakdown = new NIBreakdown();
        RulePack.NationalInsurance ni = rules.getNi();

        if (taxableIncome <= ni.getPrimaryThresholdAnnual()) {
            breakdown.totalNI = 0.0;
            return breakdown;
        }

        if (taxableIncome <= ni.getUpperEarningsLimit()) {
            breakdown.mainRateIncome = taxableIncome - ni.getPrimaryThresholdAnnual();
            breakdown.mainRateNI = breakdown.mainRateIncome * ni.getMainRate();
            breakdown.totalNI = breakdown.mainRateNI;
        } else {
            breakdown.mainRateIncome = ni.getUpperEarningsLimit() - ni.getPrimaryThresholdAnnual();
            breakdown.mainRateNI = breakdown.mainRateIncome * ni.getMainRate();
            breakdown.upperRateIncome = taxableIncome - ni.getUpperEarningsLimit();
            breakdown.upperRateNI = breakdown.upperRateIncome * ni.getUpperRate();
            breakdown.totalNI = breakdown.mainRateNI + breakdown.upperRateNI;
        }

        return breakdown;
    }

    private static class NIBreakdown {
        double mainRateIncome = 0.0;
        double mainRateNI = 0.0;
        double upperRateIncome = 0.0;
        double upperRateNI = 0.0;
        double totalNI = 0.0;
    }
}
