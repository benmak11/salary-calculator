package app.salary.calculator.engine;

import app.salary.common.constants.Country;
import app.salary.common.constants.PayCadence;
import app.salary.common.dto.*;

public class CalculationInput {
    private Country country;
    private Integer taxYear;
    private Income income;
    private PayCadence payCadence;
    private Pretax pretax;
    private Posttax posttax;
    private CountryOptionsUS usOptions;
    private CountryOptionsUK ukOptions;

    public static CalculationInput from(CalculateRequest request) {
        CalculationInput input = new CalculationInput();
        input.country = request.getCountry();
        input.taxYear = request.getTaxYear();
        input.income = request.getIncome();
        input.payCadence = request.getCadence();
        input.pretax = request.getPretax();
        input.posttax = request.getPosttax();

        if (request.getCountryOptions() != null) {
            input.usOptions = request.getCountryOptions().getUs();
            input.ukOptions = request.getCountryOptions().getUk();
        }

        return input;
    }

    public Country getCountry() { return country; }
    public void setCountry(Country country) { this.country = country; }
    public Integer getTaxYear() { return taxYear; }
    public void setTaxYear(Integer taxYear) { this.taxYear = taxYear; }
    public Income getIncome() { return income; }
    public void setIncome(Income income) { this.income = income; }
    public PayCadence getPayCadence() { return payCadence; }
    public void setPayCadence(PayCadence payCadence) { this.payCadence = payCadence; }
    public Pretax getPretax() { return pretax; }
    public void setPretax(Pretax pretax) { this.pretax = pretax; }
    public Posttax getPosttax() { return posttax; }
    public void setPosttax(Posttax posttax) { this.posttax = posttax; }
    public CountryOptionsUS getUsOptions() { return usOptions; }
    public void setUsOptions(CountryOptionsUS usOptions) { this.usOptions = usOptions; }
    public CountryOptionsUK getUkOptions() { return ukOptions; }
    public void setUkOptions(CountryOptionsUK ukOptions) { this.ukOptions = ukOptions; }
}
