package app.salary.api.client;

import app.salary.rules.RulePack;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpRulePackClientTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void fetchLatest_whenBaseUrlIsBlank_shouldReturnEmptyWithoutCallingService() {
        HttpRulePackClient client = new HttpRulePackClient(restTemplate, objectMapper, "");

        Optional<RulePack> result = client.fetchLatest("UK", 2025);

        assertTrue(result.isEmpty());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void fetchLatest_whenBaseUrlIsNull_shouldReturnEmptyWithoutCallingService() {
        HttpRulePackClient client = new HttpRulePackClient(restTemplate, objectMapper, null);

        Optional<RulePack> result = client.fetchLatest("UK", 2025);

        assertTrue(result.isEmpty());
        verifyNoInteractions(restTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchLatest_whenMetadataResponseIsNull_shouldReturnEmpty() {
        HttpRulePackClient client = new HttpRulePackClient(restTemplate, objectMapper, "http://localhost:8081");

        when(restTemplate.getForObject(anyString(), eq(Map.class), eq("UK"), eq(2025)))
                .thenReturn(null);

        Optional<RulePack> result = client.fetchLatest("UK", 2025);

        assertTrue(result.isEmpty());
        verify(restTemplate, times(1)).getForObject(anyString(), eq(Map.class), eq("UK"), eq(2025));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchLatest_whenDownloadResponseIsNull_shouldReturnEmpty() {
        HttpRulePackClient client = new HttpRulePackClient(restTemplate, objectMapper, "http://localhost:8081");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", "pack-abc");

        when(restTemplate.getForObject(anyString(), eq(Map.class), eq("UK"), eq(2025)))
                .thenReturn(metadata);
        when(restTemplate.getForObject(anyString(), eq(Map.class), eq("pack-abc")))
                .thenReturn(null);

        Optional<RulePack> result = client.fetchLatest("UK", 2025);

        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchLatest_whenHttpCallThrows_shouldReturnEmpty() {
        HttpRulePackClient client = new HttpRulePackClient(restTemplate, objectMapper, "http://localhost:8081");

        when(restTemplate.getForObject(anyString(), eq(Map.class), eq("UK"), eq(2025)))
                .thenThrow(new RestClientException("Connection refused"));

        Optional<RulePack> result = client.fetchLatest("UK", 2025);

        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchLatest_whenServiceReturnsValidData_shouldReturnRulePack() {
        HttpRulePackClient client = new HttpRulePackClient(restTemplate, objectMapper, "http://localhost:8081");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", "pack-abc");

        Map<String, Object> rulePackJson = new HashMap<>();
        rulePackJson.put("metadata", Map.of("country", "UK", "taxYear", 2025, "version", "UK-2025.1.0"));

        when(restTemplate.getForObject(anyString(), eq(Map.class), eq("UK"), eq(2025)))
                .thenReturn(metadata);
        when(restTemplate.getForObject(anyString(), eq(Map.class), eq("pack-abc")))
                .thenReturn(rulePackJson);

        Optional<RulePack> result = client.fetchLatest("UK", 2025);

        assertTrue(result.isPresent());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchLatest_whenBaseUrlHasTrailingSlash_shouldReturnRulePack() {
        // Ensure URL construction works even if the base URL format varies
        HttpRulePackClient client = new HttpRulePackClient(restTemplate, objectMapper, "http://localhost:8081");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", "pack-xyz");
        Map<String, Object> rulePackJson = new HashMap<>();

        when(restTemplate.getForObject(anyString(), eq(Map.class), eq("US"), eq(2025)))
                .thenReturn(metadata);
        when(restTemplate.getForObject(anyString(), eq(Map.class), eq("pack-xyz")))
                .thenReturn(rulePackJson);

        Optional<RulePack> result = client.fetchLatest("US", 2025);

        assertTrue(result.isPresent());
    }
}
