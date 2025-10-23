package app.salary.common.dto;

import app.salary.common.constants.Country;
import app.salary.common.constants.PayCadence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class CalculateRequest {
    @NotNull
    private Country country;

    @NotNull
    @Min(2000)
    private Integer taxYear;

    @NotNull
    @Valid
    private Income income;

    @NotNull
    private PayCadence cadence;

    @Valid
    private Pretax pretax;

    @Valid
    private Posttax posttax;

    @Valid
    private CountryOptions countryOptions;

    public Country getCountry() { return country; }
    public void setCountry(Country country) { this.country = country; }
    public Integer getTaxYear() { return taxYear; }
    public void setTaxYear(Integer taxYear) { this.taxYear = taxYear; }
    public Income getIncome() { return income; }
    public void setIncome(Income income) { this.income = income; }
    public PayCadence getCadence() { return cadence; }
    public void setCadence(PayCadence cadence) { this.cadence = cadence; }
    public Pretax getPretax() { return pretax; }
    public void setPretax(Pretax pretax) { this.pretax = pretax; }
    public Posttax getPosttax() { return posttax; }
    public void setPosttax(Posttax posttax) { this.posttax = posttax; }
    public CountryOptions getCountryOptions() { return countryOptions; }
    public void setCountryOptions(CountryOptions countryOptions) { this.countryOptions = countryOptions; }
}
