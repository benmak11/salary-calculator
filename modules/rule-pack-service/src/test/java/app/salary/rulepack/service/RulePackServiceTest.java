package app.salary.rulepack.service;

import app.salary.rulepack.dto.RulePackDto;
import app.salary.rulepack.entity.RulePackEntity;
import app.salary.rulepack.entity.RulePackStatus;
import app.salary.rulepack.repository.RulePackRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RulePackServiceTest {

    @Mock
    private RulePackRepository repository;

    @Mock
    private RulePackStorageService storageService;

    @Mock
    private PubSubTemplate pubSubTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private RulePackService service;

    @BeforeEach
    void setUp() {
        service = new RulePackService(repository, storageService, pubSubTemplate, objectMapper);
    }

    @Test
    void findRulePacks_shouldReturnPageOfRulePacks() {
        Pageable pageable = PageRequest.of(0, 10);
        RulePackEntity entity = createTestEntity();

        when(repository.findAll()).thenReturn(Flux.just(entity));

        Page<RulePackDto> result = service.findRulePacks("US", 2025, "PUBLISHED", pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("US", result.getContent().get(0).getCountry());
    }

    @Test
    void findLatest_shouldReturnLatestPublishedRulePack() {
        RulePackEntity entity = createTestEntity();

        when(repository.findAll()).thenReturn(Flux.just(entity));

        RulePackDto result = service.findLatest("US", 2025);

        assertNotNull(result);
        assertEquals("US", result.getCountry());
        assertEquals(2025, result.getTaxYear());
    }

    @Test
    void findLatest_whenNotFound_shouldReturnNull() {
        when(repository.findAll()).thenReturn(Flux.empty());

        RulePackDto result = service.findLatest("XX", 2025);

        assertNull(result);
    }

    @Test
    void findById_shouldReturnRulePack() {
        RulePackEntity entity = createTestEntity();

        when(repository.findById("test-id")).thenReturn(Mono.just(entity));

        Optional<RulePackDto> result = service.findById("test-id");

        assertTrue(result.isPresent());
        assertEquals("test-id", result.get().getId());
    }

    @Test
    void findById_whenNotFound_shouldReturnEmpty() {
        when(repository.findById("nonexistent")).thenReturn(Mono.empty());

        Optional<RulePackDto> result = service.findById("nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void downloadRulePack_shouldReturnRulePackContent() {
        RulePackEntity entity = createTestEntity();
        Map<String, Object> content = Map.of("key", "value");

        when(repository.findById("test-id")).thenReturn(Mono.just(entity));
        when(storageService.download("storage/path")).thenReturn(Optional.of(content));

        Optional<Map<String, Object>> result = service.downloadRulePack("test-id");

        assertTrue(result.isPresent());
        assertEquals("value", result.get().get("key"));
    }

    @Test
    void downloadRulePack_whenNotFound_shouldReturnEmpty() {
        when(repository.findById("nonexistent")).thenReturn(Mono.empty());

        Optional<Map<String, Object>> result = service.downloadRulePack("nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void createRulePack_shouldCreateNewRulePack() {
        Map<String, Object> rulePackJson = Map.of("rule", "value");

        when(repository.findAll()).thenReturn(Flux.empty()); // no existing packs
        when(storageService.upload("US", 2025, "2025.1.0", rulePackJson))
                .thenReturn("storage/path");
        when(repository.save(any(RulePackEntity.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        RulePackDto result = service.createRulePack(
                "US", 2025, "2025.1.0", LocalDate.of(2025, 1, 1), rulePackJson);

        assertNotNull(result);
        assertEquals("US", result.getCountry());
        assertEquals(2025, result.getTaxYear());
        assertEquals("2025.1.0", result.getVersion());
        assertEquals(RulePackStatus.DRAFT, result.getStatus());

        verify(storageService).upload("US", 2025, "2025.1.0", rulePackJson);
        verify(repository).save(any(RulePackEntity.class));
    }

    @Test
    void createRulePack_whenDuplicateExists_shouldThrowException() {
        Map<String, Object> rulePackJson = Map.of("rule", "value");
        RulePackEntity existing = RulePackEntity.builder()
                .countryCode("US").taxYear(2025).version("2025.1.0")
                .status(RulePackStatus.DRAFT).build();

        when(repository.findAll()).thenReturn(Flux.just(existing));

        assertThrows(IllegalArgumentException.class, () ->
                service.createRulePack("US", 2025, "2025.1.0", LocalDate.of(2025, 1, 1), rulePackJson));

        verify(storageService, never()).upload(any(), any(), any(), any());
    }

    @Test
    void publishRulePack_shouldUpdateStatusToPublished() throws Exception {
        RulePackEntity entity = createTestEntity();
        entity.setStatus(RulePackStatus.DRAFT);

        when(repository.findById("test-id")).thenReturn(Mono.just(entity));
        when(repository.save(any(RulePackEntity.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        RulePackDto result = service.publishRulePack("test-id");

        assertEquals(RulePackStatus.PUBLISHED, result.getStatus());
        verify(repository).save(entity);
    }

    @Test
    void publishRulePack_whenAlreadyPublished_shouldThrowException() {
        RulePackEntity entity = createTestEntity();
        entity.setStatus(RulePackStatus.PUBLISHED);

        when(repository.findById("test-id")).thenReturn(Mono.just(entity));

        assertThrows(IllegalStateException.class, () ->
                service.publishRulePack("test-id"));
    }

    @Test
    void publishRulePack_whenNotFound_shouldThrowException() {
        when(repository.findById("nonexistent")).thenReturn(Mono.empty());

        assertThrows(RuntimeException.class, () ->
                service.publishRulePack("nonexistent"));
    }

    @Test
    void deprecateRulePack_shouldUpdateStatusToDeprecated() throws Exception {
        RulePackEntity entity = createTestEntity();

        when(repository.findById("test-id")).thenReturn(Mono.just(entity));
        when(repository.save(any(RulePackEntity.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        RulePackDto result = service.deprecateRulePack("test-id");

        assertEquals(RulePackStatus.DEPRECATED, result.getStatus());
        assertNotNull(result.getDeprecationDate());
        verify(repository).save(entity);
    }

    @Test
    void deprecateRulePack_whenNotFound_shouldThrowException() {
        when(repository.findById("nonexistent")).thenReturn(Mono.empty());

        assertThrows(RuntimeException.class, () ->
                service.deprecateRulePack("nonexistent"));
    }

    private RulePackEntity createTestEntity() {
        return RulePackEntity.builder()
                .id("test-id")
                .countryCode("US")
                .taxYear(2025)
                .version("2025.1.0")
                .status(RulePackStatus.PUBLISHED)
                .effectiveDate(new Date())
                .checksum("abc123")
                .storagePath("storage/path")
                .createdAt(new Date())
                .createdBy("system")
                .build();
    }
}
