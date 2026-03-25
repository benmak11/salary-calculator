package app.salary.rulepack.service;

import app.salary.rulepack.dto.RulePackDto;
import app.salary.rulepack.entity.RulePackEntity;
import app.salary.rulepack.entity.RulePackStatus;
import app.salary.rulepack.event.RulePackLifecycleEvent;
import app.salary.rulepack.repository.RulePackRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for rule pack operations.
 * Coordinates between controller, Firestore, GCS, Redis cache, and Pub/Sub.
 */
@Service
public class RulePackService {

    private static final Logger logger = LoggerFactory.getLogger(RulePackService.class);

    private final RulePackRepository repository;
    private final RulePackStorageService storageService;
    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gcp.pubsub.topic.rulepack-lifecycle:rule-pack-lifecycle}")
    private String lifecycleTopic;

    @Autowired
    public RulePackService(
            RulePackRepository repository,
            RulePackStorageService storageService,
            PubSubTemplate pubSubTemplate,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.storageService = storageService;
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Find rule packs with optional filters and pagination.
     * Filtering is performed in-memory after loading all Firestore documents.
     */
    public Page<RulePackDto> findRulePacks(
            String country,
            Integer taxYear,
            String status,
            Pageable pageable
    ) {
        RulePackStatus rulePackStatus = RulePackStatus.valueOf(status);

        List<RulePackEntity> all = repository.findAll().collectList().block();
        if (all == null) all = Collections.emptyList();

        List<RulePackDto> filtered = all.stream()
                .filter(e -> country == null || country.equals(e.getCountryCode()))
                .filter(e -> taxYear == null || taxYear.equals(e.getTaxYear()))
                .filter(e -> rulePackStatus.equals(e.getStatus()))
                .sorted(Comparator.comparing(RulePackEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toDto)
                .collect(Collectors.toList());

        long total = filtered.size();
        int fromIndex = (int) pageable.getOffset();
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), filtered.size());
        List<RulePackDto> page = fromIndex <= filtered.size()
                ? filtered.subList(fromIndex, toIndex)
                : Collections.emptyList();

        return new PageImpl<>(page, pageable, total);
    }

    /**
     * Find latest published rule pack with Redis caching.
     */
    @Cacheable(value = "rulepack", key = "#country + ':' + #taxYear", unless = "#result == null")
    public RulePackDto findLatest(String country, Integer taxYear) {
        logger.debug("Finding latest rule pack for {} {}", country, taxYear);

        List<RulePackEntity> candidates = repository.findAll()
                .filter(e -> country.equals(e.getCountryCode()))
                .filter(e -> taxYear.equals(e.getTaxYear()))
                .filter(e -> RulePackStatus.PUBLISHED.equals(e.getStatus()))
                .collectSortedList(Comparator.comparing(RulePackEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .block();

        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return toDto(candidates.get(0));
    }

    /**
     * Get rule pack by ID.
     */
    public Optional<RulePackDto> findById(String id) {
        return repository.findById(id).blockOptional().map(this::toDto);
    }

    /**
     * Download rule pack JSON content from GCS.
     */
    public Optional<Map<String, Object>> downloadRulePack(String id) {
        return findById(id)
                .flatMap(dto -> storageService.download(dto.getStoragePath()));
    }

    /**
     * Create new rule pack in DRAFT status, uploading content to GCS.
     */
    public RulePackDto createRulePack(
            String country,
            Integer taxYear,
            String version,
            LocalDate effectiveDate,
            Map<String, Object> rulePackJson
    ) {
        boolean exists = Boolean.TRUE.equals(
                repository.findAll()
                        .filter(e -> country.equals(e.getCountryCode()))
                        .filter(e -> taxYear.equals(e.getTaxYear()))
                        .filter(e -> version.equals(e.getVersion()))
                        .hasElements()
                        .block()
        );
        if (exists) {
            logger.warn("Attempted to create duplicate rule pack: {}-{}-{}", country, taxYear, version);
            throw new IllegalArgumentException(
                    String.format("Rule pack already exists: %s-%d-%s", country, taxYear, version)
            );
        }

        String checksum = calculateChecksum(rulePackJson);
        String storagePath = storageService.upload(country, taxYear, version, rulePackJson);

        Date now = new Date();
        RulePackEntity entity = RulePackEntity.builder()
                .id(UUID.randomUUID().toString())
                .countryCode(country)
                .taxYear(taxYear)
                .version(version)
                .status(RulePackStatus.DRAFT)
                .effectiveDate(effectiveDate != null ? java.sql.Date.valueOf(effectiveDate) : null)
                .checksum(checksum)
                .storagePath(storagePath)
                .createdAt(now)
                .updatedAt(now)
                .createdBy("system")
                .build();

        RulePackEntity saved = repository.save(entity).block();
        logger.info("Created rule pack: {} ({})", saved.getId(), saved.getVersion());

        return toDto(saved);
    }

    /**
     * Publish rule pack (DRAFT -> PUBLISHED) and emit a lifecycle event.
     */
    @CacheEvict(value = "rulepack", allEntries = true)
    public RulePackDto publishRulePack(String id) {
        RulePackEntity entity = repository.findById(id).blockOptional()
                .orElseThrow(() -> {
                    logger.warn("Attempted to publish non-existent rule pack: {}", id);
                    return new RuntimeException("Rule pack not found: " + id);
                });

        if (entity.getStatus() == RulePackStatus.PUBLISHED) {
            logger.warn("Attempted to publish already published rule pack: {}", id);
            throw new IllegalStateException("Rule pack is already published");
        }

        entity.setStatus(RulePackStatus.PUBLISHED);
        entity.setUpdatedAt(new Date());

        RulePackEntity updated = repository.save(entity).block();
        logger.info("Published rule pack: {} ({})", updated.getId(), updated.getVersion());

        publishLifecycleEvent("RULE_PACK_PUBLISHED", updated);

        return toDto(updated);
    }

    /**
     * Deprecate rule pack and emit a lifecycle event.
     */
    @CacheEvict(value = "rulepack", allEntries = true)
    public RulePackDto deprecateRulePack(String id) {
        RulePackEntity entity = repository.findById(id).blockOptional()
                .orElseThrow(() -> {
                    logger.warn("Attempted to deprecate non-existent rule pack: {}", id);
                    return new RuntimeException("Rule pack not found: " + id);
                });

        entity.setStatus(RulePackStatus.DEPRECATED);
        entity.setDeprecationDate(new Date());
        entity.setUpdatedAt(new Date());

        RulePackEntity updated = repository.save(entity).block();
        logger.info("Deprecated rule pack: {} ({})", updated.getId(), updated.getVersion());

        publishLifecycleEvent("RULE_PACK_DEPRECATED", updated);

        return toDto(updated);
    }

    private void publishLifecycleEvent(String eventType, RulePackEntity entity) {
        try {
            RulePackLifecycleEvent event = new RulePackLifecycleEvent(
                    eventType,
                    entity.getCountryCode(),
                    entity.getTaxYear(),
                    entity.getVersion(),
                    entity.getStoragePath()
            );
            String payload = objectMapper.writeValueAsString(event);
            pubSubTemplate.publish(lifecycleTopic, payload);
            logger.info("Published {} event for rule pack {} to topic {}",
                    eventType, entity.getId(), lifecycleTopic);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize lifecycle event for rule pack {}", entity.getId(), e);
        } catch (Exception e) {
            logger.error("Failed to publish lifecycle event for rule pack {}", entity.getId(), e);
        }
    }

    private String calculateChecksum(Map<String, Object> rulePackJson) {
        try {
            return DigestUtils.sha256Hex(rulePackJson.toString());
        } catch (Exception e) {
            logger.error("Failed to calculate checksum", e);
            throw new RuntimeException("Failed to calculate checksum", e);
        }
    }

    private RulePackDto toDto(RulePackEntity entity) {
        RulePackDto dto = new RulePackDto();
        dto.setId(entity.getId());
        dto.setCountry(entity.getCountryCode());
        dto.setTaxYear(entity.getTaxYear());
        dto.setVersion(entity.getVersion());
        dto.setStatus(entity.getStatus());
        dto.setEffectiveDate(toLocalDate(entity.getEffectiveDate()));
        dto.setDeprecationDate(toLocalDate(entity.getDeprecationDate()));
        dto.setChecksum(entity.getChecksum());
        dto.setStoragePath(entity.getStoragePath());
        dto.setCreatedAt(toLocalDateTime(entity.getCreatedAt()));
        dto.setUpdatedAt(toLocalDateTime(entity.getUpdatedAt()));
        dto.setCreatedBy(entity.getCreatedBy());
        return dto;
    }

    private LocalDate toLocalDate(Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
