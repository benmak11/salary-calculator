package app.salary.calculator.engine;

import app.salary.common.constants.Country;
import app.salary.common.constants.FilingStatus;
import app.salary.common.constants.PayCadence;
import app.salary.common.dto.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CalculationInputTest {

    @Test
    void from_withAllFields_shouldMapCorrectly() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.US);
        request.setTaxYear(2025);
        request.setAnnualSalary(100000.0);
        request.setCadence(PayCadence.MONTHLY);

        Pretax pretax = new Pretax();
        pretax.setPercent(0.05);
        pretax.setHsa(3000.0);
        request.setPretax(pretax);

        Posttax posttax = new Posttax();
        posttax.setFixed(100.0);
        request.setPosttax(posttax);

        CountryOptions countryOptions = new CountryOptions();
        CountryOptionsUS usOptions = new CountryOptionsUS();
        usOptions.setState("CA");
        usOptions.setFilingStatus(FilingStatus.SINGLE);
        usOptions.setAllowances(1);
        countryOptions.setUs(usOptions);
        request.setCountryOptions(countryOptions);

        CalculationInput input = CalculationInput.from(request);

        assertNotNull(input);
        assertEquals(Country.US, input.getCountry());
        assertEquals(2025, input.getTaxYear());
        assertEquals(100000.0, input.getAnnualGross());
        assertEquals(PayCadence.MONTHLY, input.getPayCadence());
        assertEquals(pretax, input.getPretax());
        assertEquals(posttax, input.getPosttax());
        assertEquals(usOptions, input.getUsOptions());
        assertNull(input.getUkOptions());
    }

    @Test
    void from_withNullPretax_shouldCreateDefaultPretax() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.UK);
        request.setTaxYear(2025);
        request.setAnnualSalary(50000.0);
        request.setPretax(null);

        CalculationInput input = CalculationInput.from(request);

        assertNotNull(input.getPretax());
        assertEquals(0.0, input.getPretax().getPercent());
        assertEquals(0.0, input.getPretax().getFixed());
    }

    @Test
    void from_withNullPosttax_shouldCreateDefaultPosttax() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.UK);
        request.setTaxYear(2025);
        request.setAnnualSalary(50000.0);
        request.setPosttax(null);

        CalculationInput input = CalculationInput.from(request);

        assertNotNull(input.getPosttax());
        assertEquals(0.0, input.getPosttax().getFixed());
        assertNull(input.getPosttax().getStudentLoanPlan());
    }

    @Test
    void from_withNullCountryOptions_shouldHaveNullUsAndUkOptions() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.UK);
        request.setTaxYear(2025);
        request.setAnnualSalary(50000.0);
        request.setCountryOptions(null);

        CalculationInput input = CalculationInput.from(request);

        assertNull(input.getUsOptions());
        assertNull(input.getUkOptions());
    }

    @Test
    void from_withUkCountryOptions_shouldMapUkOptions() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.UK);
        request.setTaxYear(2025);
        request.setAnnualSalary(50000.0);

        CountryOptions countryOptions = new CountryOptions();
        CountryOptionsUK ukOptions = new CountryOptionsUK();
        ukOptions.setTaxCode("1257L");
        ukOptions.setScottishResident(true);
        ukOptions.setNiCategory("A");
        countryOptions.setUk(ukOptions);
        request.setCountryOptions(countryOptions);

        CalculationInput input = CalculationInput.from(request);

        assertNotNull(input.getUkOptions());
        assertEquals("1257L", input.getUkOptions().getTaxCode());
        assertEquals(true, input.getUkOptions().getScottishResident());
        assertEquals("A", input.getUkOptions().getNiCategory());
        assertNull(input.getUsOptions());
    }

    @Test
    void from_withMinimalRequest_shouldUseDefaults() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.UK);
        request.setTaxYear(2025);
        request.setAnnualSalary(40000.0);
        request.setCadence(PayCadence.ANNUAL);

        CalculationInput input = CalculationInput.from(request);

        assertNotNull(input);
        assertEquals(Country.UK, input.getCountry());
        assertEquals(2025, input.getTaxYear());
        assertEquals(40000.0, input.getAnnualGross());
        assertEquals(PayCadence.ANNUAL, input.getPayCadence());
        assertNotNull(input.getPretax());
        assertNotNull(input.getPosttax());
    }

    @Test
    void from_withUsMarriedFilingStatus_shouldMapCorrectly() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.US);
        request.setTaxYear(2025);
        request.setAnnualSalary(150000.0);

        CountryOptions countryOptions = new CountryOptions();
        CountryOptionsUS usOptions = new CountryOptionsUS();
        usOptions.setState("NY");
        usOptions.setFilingStatus(FilingStatus.MARRIED);
        usOptions.setAllowances(2);
        countryOptions.setUs(usOptions);
        request.setCountryOptions(countryOptions);

        CalculationInput input = CalculationInput.from(request);

        assertNotNull(input.getUsOptions());
        assertEquals("NY", input.getUsOptions().getState());
        assertEquals(FilingStatus.MARRIED, input.getUsOptions().getFilingStatus());
        assertEquals(2, input.getUsOptions().getAllowances());
    }

    @Test
    void gettersAndSetters_shouldWorkCorrectly() {
        CalculationInput input = new CalculationInput();

        input.setCountry(Country.US);
        assertEquals(Country.US, input.getCountry());

        input.setTaxYear(2025);
        assertEquals(2025, input.getTaxYear());

        input.setAnnualGross(75000.0);
        assertEquals(75000.0, input.getAnnualGross());

        input.setPayCadence(PayCadence.WEEKLY);
        assertEquals(PayCadence.WEEKLY, input.getPayCadence());

        Pretax pretax = new Pretax();
        input.setPretax(pretax);
        assertEquals(pretax, input.getPretax());

        Posttax posttax = new Posttax();
        input.setPosttax(posttax);
        assertEquals(posttax, input.getPosttax());

        CountryOptionsUS usOptions = new CountryOptionsUS();
        input.setUsOptions(usOptions);
        assertEquals(usOptions, input.getUsOptions());

        CountryOptionsUK ukOptions = new CountryOptionsUK();
        input.setUkOptions(ukOptions);
        assertEquals(ukOptions, input.getUkOptions());
    }
}
