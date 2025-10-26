package app.salary.calculator.registry;

import app.salary.calculator.engine.CountryCalculator;
import app.salary.common.constants.Country;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class CalculatorRegistry {
    private static final Logger log = LoggerFactory.getLogger(CalculatorRegistry.class);

    private final List<CountryCalculator> calculators;

    @Autowired
    public CalculatorRegistry(List<CountryCalculator> calculators) {
        this.calculators = calculators;
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║  Calculator Registry Initialized            ║");
        log.info("╠══════════════════════════════════════════════╣");
        log.info("║  Registered {} country calculator(s):", calculators.size());
        calculators.forEach(calc ->
                log.info("║    ✓ {}", calc.getClass().getSimpleName())
        );
        log.info("╚══════════════════════════════════════════════╝");
    }

    public CountryCalculator getCalculator(Country country, int taxYear) {
        return calculators.stream()
                .filter(calc -> calc.supports(country, taxYear))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("No calculator found for country %s and tax year %d",
                                country, taxYear)
                ));
    }

    public List<Country> getSupportedCountries() {
        return calculators.stream()
                .map(this::extractSupportedCountry)
                .distinct()
                .collect(Collectors.toList());
    }

    public int getCalculatorCount() {
        return calculators.size();
    }

    public boolean isCountrySupported(Country country, int taxYear) {
        return calculators.stream()
                .anyMatch(calc -> calc.supports(country, taxYear));
    }

    private Country extractSupportedCountry(CountryCalculator calculator) {
        for (Country country : Country.values()) {
            if (calculator.supports(country, 2025)) {
                return country;
            }
        }
        return null;
    }
}
