package app.salary.api.config;

import app.salary.calculator.engine.CalculationOrchestrator;
import app.salary.calculator.registry.CalculatorRegistry;
import app.salary.rules.RulesRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = {
        "app.salary.calculator.countries",
        "app.salary.calculator.shared",
        "app.salary.calculator.registry"
})
public class CalculatorConfig {

    @Bean
    public RulesRegistry rulesRegistry() {
        return new RulesRegistry();
    }

    @Bean
    public CalculationOrchestrator calculationOrchestrator(
            RulesRegistry rulesRegistry,
            CalculatorRegistry calculatorRegistry) {
        return new CalculationOrchestrator(rulesRegistry, calculatorRegistry);
    }
}
