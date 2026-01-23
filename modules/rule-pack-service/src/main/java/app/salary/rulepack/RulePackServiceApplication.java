package app.salary.rulepack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for Rule Pack Service
 *
 * Features:
 * - RESTful API for rule pack management
 * - Google Cloud Storage integration
 * - PostgreSQL metadata storage
 * - Caching and performance optimization
 * - Comprehensive logging and metrics
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class RulePackServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RulePackServiceApplication.class, args);
    }
}
