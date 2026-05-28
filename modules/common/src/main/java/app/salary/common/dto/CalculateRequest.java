package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import app.salary.common.constants.Country;
import app.salary.common.constants.PayCadence;
import app.salary.common.validation.ValidCountryOptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@ExcludeFromCodeCoverage
@ValidCountryOptions
@Schema(description = "Request to calculate net salary with tax breakdown")
public class CalculateRequest {
    @NotNull
    @Schema(description = "Country code", example = "US")
    private Country country;

    @NotNull
    @Min(2025)
    @Schema(description = "Tax year for calculation", example = "2025")
    private Integer taxYear;

    @Min(0)
    @Schema(description = "Annual base salary (excluding bonus). Legacy: provide this OR `earnings`. Engine prefers `earnings` when both are set.", example = "100000")
    private Double annualSalary;

    @Min(0)
    @Schema(description = "Annual bonus / supplemental wages taxed at flat 22% federal rate (US only). Mirrored by earnings.bonus when set.", example = "10000")
    private Double bonus = 0.0;

    @Valid
    @Schema(description = "Structured earnings (salary OR hourly + bonus/commission). Optional in Phase 1 (engine ignores it); will become the primary input in Phase 2.")
    private Earnings earnings;

    @Schema(description = "Pay date for this paycheck (ISO-8601 yyyy-MM-dd). Accepted but currently informational only.", example = "2026-05-28")
    private String payDate;

    @Schema(description = "Pay frequency (defaults to ANNUAL)", example = "ANNUAL")
    private PayCadence cadence = PayCadence.ANNUAL;

    @Valid
    @Schema(description = "Pre-tax deductions (optional)")
    private Pretax pretax;

    @Valid
    @Schema(description = "Post-tax deductions (optional)")
    private Posttax posttax;

    @Valid
    @Schema(description = "Country-specific options")
    private CountryOptions countryOptions;

    public Country getCountry() { return country; }
    public void setCountry(Country country) { this.country = country; }
    public Integer getTaxYear() { return taxYear; }
    public void setTaxYear(Integer taxYear) { this.taxYear = taxYear; }
    public Double getAnnualSalary() { return annualSalary; }
    public void setAnnualSalary(Double annualSalary) { this.annualSalary = annualSalary; }
    public Double getBonus() { return bonus; }
    public void setBonus(Double bonus) { this.bonus = bonus; }
    public Earnings getEarnings() { return earnings; }
    public void setEarnings(Earnings earnings) { this.earnings = earnings; }
    public String getPayDate() { return payDate; }
    public void setPayDate(String payDate) { this.payDate = payDate; }
    public PayCadence getCadence() { return cadence; }
    public void setCadence(PayCadence cadence) { this.cadence = cadence; }
    public Pretax getPretax() { return pretax; }
    public void setPretax(Pretax pretax) { this.pretax = pretax; }
    public Posttax getPosttax() { return posttax; }
    public void setPosttax(Posttax posttax) { this.posttax = posttax; }
    public CountryOptions getCountryOptions() { return countryOptions; }
    public void setCountryOptions(CountryOptions countryOptions) { this.countryOptions = countryOptions; }
}
