package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import app.salary.common.constants.Country;
import app.salary.common.constants.PayCadence;
import app.salary.common.validation.ValidCountryOptions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@ExcludeFromCodeCoverage
@ValidCountryOptions
public class CalculateRequest {
    @NotNull
    private Country country;

    @NotNull
    @Min(2025)
    private Integer taxYear;

    @NotNull
    @Min(0)
    private Double annualSalary;

    private PayCadence cadence = PayCadence.ANNUAL;

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
    public Double getAnnualSalary() { return annualSalary; }
    public void setAnnualSalary(Double annualSalary) { this.annualSalary = annualSalary; }
    public PayCadence getCadence() { return cadence; }
    public void setCadence(PayCadence cadence) { this.cadence = cadence; }
    public Pretax getPretax() { return pretax; }
    public void setPretax(Pretax pretax) { this.pretax = pretax; }
    public Posttax getPosttax() { return posttax; }
    public void setPosttax(Posttax posttax) { this.posttax = posttax; }
    public CountryOptions getCountryOptions() { return countryOptions; }
    public void setCountryOptions(CountryOptions countryOptions) { this.countryOptions = countryOptions; }
}
