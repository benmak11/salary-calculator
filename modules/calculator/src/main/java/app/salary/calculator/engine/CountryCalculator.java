package app.salary.calculator.engine;

import app.salary.common.constants.Country;
import app.salary.rules.RulePack;

public interface CountryCalculator {
    boolean supports(Country country, int taxYear);
    CalculationResult calculate(CalculationInput input, RulePack rules);
    default String getCountryCode() {
        return this.getClass().getSimpleName().replace("Calculator", "");
    }

}
