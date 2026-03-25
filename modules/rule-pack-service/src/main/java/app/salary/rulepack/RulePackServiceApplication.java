package app.salary.rulepack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for Rule Pack Service
 *
 * Features:
 * - RESTful API and GraphQL endpoint for rule pack management
 * - Google Cloud Storage for rule pack content
 * - Firestore for rule pack metadata
 * - Redis (Cloud Memorystore) distributed caching
 * - Pub/Sub lifecycle events on publish/deprecate
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
