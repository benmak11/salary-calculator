package app.salary.rulepack.service;

import app.salary.rulepack.cache.RulePackCache;
import app.salary.rulepack.dto.RulePackDto;
import app.salary.rulepack.entity.RulePackEntity;
import app.salary.rulepack.entity.RulePackStatus;
import app.salary.rulepack.event.RulePackLifecyclePublisher;
import app.salary.rulepack.repository.RulePackRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RulePackServiceTest {

    private RulePackRepository repository;
    private RulePackStorageService storageService;
    private RulePackLifecyclePublisher lifecyclePublisher;
    private RulePackService service;

    @BeforeEach
    void setUp() {
        repository = mock(RulePackRepository.class);
        storageService = mock(RulePackStorageService.class);
        lifecyclePublisher = mock(RulePackLifecyclePublisher.class);
        // Real cache — pure Caffeine, lets the tests observe caching + invalidation behavior.
        service = new RulePackService(repository, storageService, lifecyclePublisher, new RulePackCache());
    }

    private RulePackEntity entity(String id, String country, int year, String version,
                                  RulePackStatus status, Date createdAt) {
        return RulePackEntity.builder()
                .id(id)
                .countryCode(country)
                .taxYear(year)
                .version(version)
                .status(status)
                .checksum("abc123")
                .storagePath("rulepack/" + country + "/" + year + "/" + version + ".json")
                .createdAt(createdAt)
                .createdBy("system")
                .build();
    }

    private static Date date(String iso) {
        return Date.from(Instant.parse(iso));
    }

    // ── findRulePacks ────────────────────────────────────────────────────────

    @Test
    void findRulePacks_filtersByCountryYearAndStatus() {
        when(repository.findAll()).thenReturn(List.of(
                entity("us-pub", "US", 2025, "1", RulePackStatus.PUBLISHED, date("2025-01-01T00:00:00Z")),
                entity("uk-pub", "UK", 2025, "1", RulePackStatus.PUBLISHED, date("2025-01-01T00:00:00Z")),
                entity("us-draft", "US", 2025, "2", RulePackStatus.DRAFT, date("2025-01-01T00:00:00Z")),
                entity("us-2024", "US", 2024, "1", RulePackStatus.PUBLISHED, date("2024-01-01T00:00:00Z"))
        ));

        RulePackService.PageResult<RulePackDto> result =
                service.findRulePacks("US", 2025, "PUBLISHED", 0, 10);

        assertEquals(1, result.total());
        assertEquals("us-pub", result.content().get(0).getId());
    }

    @Test
    void findRulePacks_withNullCountryAndYear_matchesAll() {
        when(repository.findAll()).thenReturn(List.of(
                entity("us", "US", 2025, "1", RulePackStatus.PUBLISHED, date("2025-01-01T00:00:00Z")),
                entity("uk", "UK", 2024, "1", RulePackStatus.PUBLISHED, date("2025-02-01T00:00:00Z"))
        ));

        RulePackService.PageResult<RulePackDto> result =
                service.findRulePacks(null, null, "PUBLISHED", 0, 10);

        assertEquals(2, result.total());
    }

    @Test
    void findRulePacks_sortsByCreatedAtDescendingWithNullsLast() {
        when(repository.findAll()).thenReturn(List.of(
                entity("old", "US", 2025, "1", RulePackStatus.PUBLISHED, date("2025-01-01T00:00:00Z")),
                entity("new", "US", 2025, "2", RulePackStatus.PUBLISHED, date("2025-06-01T00:00:00Z")),
                entity("no-date", "US", 2025, "3", RulePackStatus.PUBLISHED, null)
        ));

        List<RulePackDto> content = service.findRulePacks("US", 2025, "PUBLISHED", 0, 10).content();

        assertEquals(List.of("new", "old", "no-date"),
                content.stream().map(RulePackDto::getId).toList());
    }

    @Test
    void findRulePacks_paginates() {
        when(repository.findAll()).thenReturn(List.of(
                entity("a", "US", 2025, "1", RulePackStatus.PUBLISHED, date("2025-03-01T00:00:00Z")),
                entity("b", "US", 2025, "2", RulePackStatus.PUBLISHED, date("2025-02-01T00:00:00Z")),
                entity("c", "US", 2025, "3", RulePackStatus.PUBLISHED, date("2025-01-01T00:00:00Z"))
        ));

        RulePackService.PageResult<RulePackDto> page0 = service.findRulePacks("US", 2025, "PUBLISHED", 0, 2);
        RulePackService.PageResult<RulePackDto> page1 = service.findRulePacks("US", 2025, "PUBLISHED", 1, 2);
        RulePackService.PageResult<RulePackDto> page5 = service.findRulePacks("US", 2025, "PUBLISHED", 5, 2);

        assertEquals(2, page0.content().size());
        assertEquals(3, page0.total());
        assertEquals(1, page1.content().size());
        assertEquals("c", page1.content().get(0).getId());
        assertTrue(page5.content().isEmpty());
        assertEquals(3, page5.total());
    }

    @Test
    void findRulePacks_withUnknownStatus_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.findRulePacks("US", 2025, "NOT_A_STATUS", 0, 10));
    }

    // ── findLatest ───────────────────────────────────────────────────────────

    @Test
    void findLatest_returnsNewestPublishedPack() {
        when(repository.findAll()).thenReturn(List.of(
                entity("old", "US", 2025, "1", RulePackStatus.PUBLISHED, date("2025-01-01T00:00:00Z")),
                entity("new", "US", 2025, "2", RulePackStatus.PUBLISHED, date("2025-06-01T00:00:00Z")),
                entity("draft", "US", 2025, "3", RulePackStatus.DRAFT, date("2025-07-01T00:00:00Z"))
        ));

        RulePackDto dto = service.findLatest("US", 2025);

        assertEquals("new", dto.getId());
    }

    @Test
    void findLatest_whenNothingPublished_returnsNull() {
        when(repository.findAll()).thenReturn(List.of(
                entity("draft", "US", 2025, "1", RulePackStatus.DRAFT, date("2025-01-01T00:00:00Z"))
        ));

        assertNull(service.findLatest("US", 2025));
    }

    @Test
    void findLatest_cachesSecondLookup() {
        when(repository.findAll()).thenReturn(List.of(
                entity("pub", "US", 2025, "1", RulePackStatus.PUBLISHED, date("2025-01-01T00:00:00Z"))
        ));

        service.findLatest("US", 2025);
        service.findLatest("US", 2025);

        verify(repository, times(1)).findAll();
    }

    // ── findById / DTO mapping ───────────────────────────────────────────────

    @Test
    void findById_mapsEntityToDto() {
        RulePackEntity e = entity("id-1", "UK", 2025, "UK-2025.1.0",
                RulePackStatus.PUBLISHED, date("2025-01-15T10:30:00Z"));
        // Plain java.util.Date exercises the non-sql-Date branch of toLocalDate.
        e.setEffectiveDate(date("2025-04-06T00:00:00Z"));
        e.setUpdatedAt(date("2025-02-01T08:00:00Z"));
        when(repository.findById("id-1")).thenReturn(Optional.of(e));

        RulePackDto dto = service.findById("id-1").orElseThrow();

        assertEquals("id-1", dto.getId());
        assertEquals("UK", dto.getCountry());
        assertEquals(2025, dto.getTaxYear());
        assertEquals("UK-2025.1.0", dto.getVersion());
        assertEquals(RulePackStatus.PUBLISHED, dto.getStatus());
        assertEquals("abc123", dto.getChecksum());
        assertEquals("rulepack/UK/2025/UK-2025.1.0.json", dto.getStoragePath());
        assertEquals("system", dto.getCreatedBy());
        assertEquals(date("2025-04-06T00:00:00Z").toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                dto.getEffectiveDate());
        assertEquals(LocalDateTime.ofInstant(Instant.parse("2025-01-15T10:30:00Z"), ZoneId.systemDefault()),
                dto.getCreatedAt());
        assertNull(dto.getDeprecationDate());
    }

    @Test
    void findById_whenMissing_returnsEmpty() {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        assertTrue(service.findById("nope").isEmpty());
    }

    // ── downloadRulePack ─────────────────────────────────────────────────────

    @Test
    void downloadRulePack_fetchesPayloadFromStorage() {
        RulePackEntity e = entity("id-2", "US", 2025, "1", RulePackStatus.PUBLISHED, null);
        when(repository.findById("id-2")).thenReturn(Optional.of(e));
        when(storageService.download(e.getStoragePath()))
                .thenReturn(Optional.of(Map.of("version", "1")));

        Optional<Map<String, Object>> payload = service.downloadRulePack("id-2");

        assertEquals("1", payload.orElseThrow().get("version"));
    }

    @Test
    void downloadRulePack_cachesPayloadByIdOnSecondCall() {
        RulePackEntity e = entity("id-3", "US", 2025, "1", RulePackStatus.PUBLISHED, null);
        when(repository.findById("id-3")).thenReturn(Optional.of(e));
        when(storageService.download(e.getStoragePath()))
                .thenReturn(Optional.of(Map.of("version", "1")));

        service.downloadRulePack("id-3");
        service.downloadRulePack("id-3");

        verify(storageService, times(1)).download(e.getStoragePath());
    }

    @Test
    void downloadRulePack_whenIdUnknown_returnsEmpty() {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        assertTrue(service.downloadRulePack("nope").isEmpty());
    }

    // ── createRulePack ───────────────────────────────────────────────────────

    @Test
    void createRulePack_uploadsAndSavesDraft() {
        Map<String, Object> json = Map.of("rule", "value");
        when(repository.findAll()).thenReturn(List.of());
        when(storageService.upload("US", 2026, "US-2026.1.0", json)).thenReturn("blob/path.json");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RulePackDto dto = service.createRulePack("US", 2026, "US-2026.1.0",
                LocalDate.of(2026, Month.JANUARY, 1), json);

        assertNotNull(dto.getId());
        assertEquals(RulePackStatus.DRAFT, dto.getStatus());
        assertEquals("blob/path.json", dto.getStoragePath());
        assertEquals(LocalDate.of(2026, Month.JANUARY, 1), dto.getEffectiveDate());
        assertEquals(DigestUtils.sha256Hex(json.toString()), dto.getChecksum());
        assertEquals("system", dto.getCreatedBy());
        assertNotNull(dto.getCreatedAt());
        assertNotNull(dto.getUpdatedAt());
    }

    @Test
    void createRulePack_withNullEffectiveDate_leavesDtoDateNull() {
        when(repository.findAll()).thenReturn(List.of());
        when(storageService.upload(any(), any(), any(), any())).thenReturn("blob/path.json");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RulePackDto dto = service.createRulePack("US", 2026, "US-2026.1.0", null, Map.of());

        assertNull(dto.getEffectiveDate());
    }

    @Test
    void createRulePack_whenDuplicate_throwsAndSkipsUpload() {
        when(repository.findAll()).thenReturn(List.of(
                entity("existing", "US", 2026, "US-2026.1.0", RulePackStatus.DRAFT, null)
        ));

        // Built outside the lambda so the only thing that can throw inside it is the
        // call under test — otherwise a failure in the fixture reads as a pass.
        LocalDate effective = LocalDate.of(2026, Month.JANUARY, 1);

        assertThrows(IllegalArgumentException.class,
                () -> service.createRulePack("US", 2026, "US-2026.1.0", effective, Map.of()));
        verify(storageService, never()).upload(any(), any(), any(), any());
        verify(repository, never()).save(any());
    }

    // ── publishRulePack ──────────────────────────────────────────────────────

    @Test
    void publishRulePack_setsStatusAndEmitsEvent() {
        RulePackEntity e = entity("id-4", "US", 2025, "1", RulePackStatus.DRAFT, null);
        when(repository.findById("id-4")).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RulePackDto dto = service.publishRulePack("id-4");

        assertEquals(RulePackStatus.PUBLISHED, dto.getStatus());
        assertNotNull(dto.getUpdatedAt());
        verify(lifecyclePublisher).publish("RULE_PACK_PUBLISHED", e);
    }

    @Test
    void publishRulePack_invalidatesLatestCache() {
        RulePackEntity pub = entity("pub", "US", 2025, "1", RulePackStatus.PUBLISHED, date("2025-01-01T00:00:00Z"));
        RulePackEntity draft = entity("draft", "US", 2025, "2", RulePackStatus.DRAFT, null);
        when(repository.findAll()).thenReturn(List.of(pub, draft));
        when(repository.findById("draft")).thenReturn(Optional.of(draft));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.findLatest("US", 2025);   // primes the cache
        service.publishRulePack("draft"); // must invalidate it
        service.findLatest("US", 2025);   // must hit the repository again

        verify(repository, times(2)).findAll();
    }

    @Test
    void publishRulePack_whenMissing_throws() {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.publishRulePack("nope"));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void publishRulePack_whenAlreadyPublished_throwsIllegalState() {
        RulePackEntity e = entity("id-5", "US", 2025, "1", RulePackStatus.PUBLISHED, null);
        when(repository.findById("id-5")).thenReturn(Optional.of(e));

        assertThrows(IllegalStateException.class, () -> service.publishRulePack("id-5"));
        verify(repository, never()).save(any());
        verify(lifecyclePublisher, never()).publish(any(), any());
    }

    // ── deprecateRulePack ────────────────────────────────────────────────────

    @Test
    void deprecateRulePack_setsStatusDatesAndEmitsEvent() {
        RulePackEntity e = entity("id-6", "US", 2025, "1", RulePackStatus.PUBLISHED, null);
        when(repository.findById("id-6")).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RulePackDto dto = service.deprecateRulePack("id-6");

        assertEquals(RulePackStatus.DEPRECATED, dto.getStatus());
        assertNotNull(dto.getDeprecationDate());
        assertNotNull(dto.getUpdatedAt());
        verify(lifecyclePublisher).publish("RULE_PACK_DEPRECATED", e);
    }

    @Test
    void deprecateRulePack_whenMissing_throws() {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.deprecateRulePack("nope"));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void deprecateRulePack_savesTheMutatedEntity() {
        RulePackEntity e = entity("id-7", "US", 2025, "1", RulePackStatus.PUBLISHED, null);
        when(repository.findById("id-7")).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.deprecateRulePack("id-7");

        ArgumentCaptor<RulePackEntity> saved = ArgumentCaptor.forClass(RulePackEntity.class);
        verify(repository).save(saved.capture());
        assertEquals(RulePackStatus.DEPRECATED, saved.getValue().getStatus());
        assertNotNull(saved.getValue().getDeprecationDate());
    }
}
