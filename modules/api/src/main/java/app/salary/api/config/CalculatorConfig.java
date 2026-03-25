package app.salary.api.config;

import app.salary.api.client.HttpRulePackClient;
import app.salary.calculator.client.RulePackClient;
import app.salary.calculator.engine.CalculationOrchestrator;
import app.salary.calculator.registry.CalculatorRegistry;
import app.salary.rules.RulesRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableCaching
@ComponentScan(basePackages = {
        "app.salary.calculator.countries",
        "app.salary.calculator.shared",
        "app.salary.calculator.registry"
})
public class CalculatorConfig {

    @Value("${rulepack.service.base-url:}")
    private String rulePackServiceBaseUrl;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public RulePackClient rulePackClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        return new HttpRulePackClient(restTemplate, objectMapper, rulePackServiceBaseUrl);
    }

    @Bean
    public RulesRegistry rulesRegistry() {
        return new RulesRegistry();
    }

    @Bean
    public CalculationOrchestrator calculationOrchestrator(
            RulesRegistry rulesRegistry,
            CalculatorRegistry calculatorRegistry,
            @Autowired(required = false) RulePackClient rulePackClient) {
        return new CalculationOrchestrator(rulesRegistry, calculatorRegistry, rulePackClient);
    }
}
