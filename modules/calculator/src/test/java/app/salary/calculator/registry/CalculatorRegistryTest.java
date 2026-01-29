package app.salary.calculator.registry;

import app.salary.calculator.engine.CalculationInput;
import app.salary.calculator.engine.CalculationResult;
import app.salary.calculator.engine.CountryCalculator;
import app.salary.common.constants.Country;
import app.salary.rules.RulePack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CalculatorRegistryTest {

    private CalculatorRegistry registry;
    private CountryCalculator ukCalculator;
    private CountryCalculator usCalculator;

    @BeforeEach
    void setUp() {
        ukCalculator = new TestUKCalculator();
        usCalculator = new TestUSCalculator();
        registry = new CalculatorRegistry(Arrays.asList(ukCalculator, usCalculator));
    }

    @Test
    void getCalculator_withSupportedCountryAndYear_shouldReturnCorrectCalculator() {
        CountryCalculator result = registry.getCalculator(Country.UK, 2025);
        assertEquals(ukCalculator, result);
    }

    @Test
    void getCalculator_withUSCountry_shouldReturnUSCalculator() {
        CountryCalculator result = registry.getCalculator(Country.US, 2025);
        assertEquals(usCalculator, result);
    }

    @Test
    void getCalculator_withUnsupportedYear_shouldThrowException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.getCalculator(Country.UK, 2020)
        );
        assertTrue(exception.getMessage().contains("No calculator found"));
    }

    @Test
    void getSupportedCountries_shouldReturnAllSupportedCountries() {
        List<Country> countries = registry.getSupportedCountries();
        assertEquals(2, countries.size());
        assertTrue(countries.contains(Country.UK));
        assertTrue(countries.contains(Country.US));
    }

    @Test
    void getSupportedCountries_withEmptyRegistry_shouldReturnEmptyList() {
        CalculatorRegistry emptyRegistry = new CalculatorRegistry(Collections.emptyList());
        List<Country> countries = emptyRegistry.getSupportedCountries();
        assertTrue(countries.isEmpty());
    }

    @Test
    void getCalculatorCount_shouldReturnCorrectCount() {
        assertEquals(2, registry.getCalculatorCount());
    }

    @Test
    void getCalculatorCount_withEmptyRegistry_shouldReturnZero() {
        CalculatorRegistry emptyRegistry = new CalculatorRegistry(Collections.emptyList());
        assertEquals(0, emptyRegistry.getCalculatorCount());
    }

    @Test
    void isCountrySupported_withSupportedCountryAndYear_shouldReturnTrue() {
        assertTrue(registry.isCountrySupported(Country.UK, 2025));
        assertTrue(registry.isCountrySupported(Country.US, 2025));
    }

    @Test
    void isCountrySupported_withUnsupportedYear_shouldReturnFalse() {
        assertFalse(registry.isCountrySupported(Country.UK, 2020));
    }

    // Test implementations
    private static class TestUKCalculator implements CountryCalculator {
        @Override
        public boolean supports(Country country, int taxYear) {
            return country == Country.UK && taxYear >= 2025;
        }

        @Override
        public CalculationResult calculate(CalculationInput input, RulePack rules) {
            return new CalculationResult();
        }

        @Override
        public String getCountryCode() {
            return "UK";
        }
    }

    private static class TestUSCalculator implements CountryCalculator {
        @Override
        public boolean supports(Country country, int taxYear) {
            return country == Country.US && taxYear >= 2025;
        }

        @Override
        public CalculationResult calculate(CalculationInput input, RulePack rules) {
            return new CalculationResult();
        }

        @Override
        public String getCountryCode() {
            return "US";
        }
    }
}
