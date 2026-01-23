package app.salary.rulepack.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Google Cloud Storage and JSON processing
 */
@Configuration
public class StorageConfiguration {

    /**
     * Create Google Cloud Storage client bean
     * Uses Application Default Credentials or Workload Identity on GKE
     */
    @Bean
    public Storage googleCloudStorage() {
        return StorageOptions.getDefaultInstance().getService();
    }

    /**
     * Configure ObjectMapper for consistent JSON serialization
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.findAndRegisterModules();
        return mapper;
    }
}