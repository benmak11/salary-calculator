package app.salary.rulepack.service;

import app.salary.rulepack.dto.RulePackDto;
import app.salary.rulepack.entity.RulePackEntity;
import app.salary.rulepack.entity.RulePackStatus;
import app.salary.rulepack.repository.RulePackRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for rule pack operations
 * Coordinates between controller, database, and storage
 */
@Service
@Transactional
public class RulePackService {

    private static final Logger logger = LoggerFactory.getLogger(RulePackService.class);

    private final RulePackRepository repository;
    private final RulePackStorageService storageService;

    @Autowired
    public RulePackService(
            RulePackRepository repository,
            RulePackStorageService storageService
    ) {
        this.repository = repository;
        this.storageService = storageService;
    }

    /**
     * Find rule packs with filters and pagination
     */
    public Page<RulePackDto> findRulePacks(
            String country,
            Integer taxYear,
            String status,
            Pageable pageable
    ) {
        RulePackStatus rulePackStatus = RulePackStatus.valueOf(status);

        Page<RulePackEntity> entities = repository.findByCountryCodeAndTaxYearAndStatus(
                country,
                taxYear,
                rulePackStatus,
                pageable
        );

        return entities.map(this::toDto);
    }

    /**
     * Find latest published rule pack with caching
     */
    @Cacheable(value = "rulepack", key = "#country + ':' + #taxYear",
            unless = "#result == null")
    public Optional<RulePackDto> findLatest(String country, Integer taxYear) {
        logger.debug("Finding latest rule pack for {} {}", country, taxYear);
        return repository.findLatestPublished(country, taxYear, RulePackStatus.PUBLISHED)
                .map(this::toDto);
    }

    /**
     * Get rule pack by ID
     */
    public Optional<RulePackDto> findById(String id) {
        return repository.findById(id)
                .map(this::toDto);
    }

    /**
     * Download rule pack JSON content
     */
    public Optional<Map<String, Object>> downloadRulePack(String id) {
        return findById(id)
                .flatMap(dto -> storageService.download(dto.getStoragePath()));
    }

    /**
     * Create new rule pack in DRAFT status
     */
    public RulePackDto createRulePack(
            String country,
            Integer taxYear,
            String version,
            LocalDate effectiveDate,
            Map<String, Object> rulePackJson
    ) {
        // Validate uniqueness
        if (repository.existsByCountryCodeAndTaxYearAndVersion(country, taxYear, version)) {
            logger.warn("Attempted to create duplicate rule pack: {}-{}-{}", country, taxYear, version);
            throw new IllegalArgumentException(
                    String.format("Rule pack already exists: %s-%d-%s", country, taxYear, version)
            );
        }

        // Calculate checksum
        String checksum = calculateChecksum(rulePackJson);

        // Upload to GCS
        String storagePath = storageService.upload(country, taxYear, version, rulePackJson);

        // Create entity using builder
        LocalDateTime now = LocalDateTime.now();
        RulePackEntity entity = RulePackEntity.builder()
                .id(UUID.randomUUID().toString())
                .countryCode(country)
                .taxYear(taxYear)
                .version(version)
                .status(RulePackStatus.DRAFT)
                .effectiveDate(effectiveDate)
                .checksum(checksum)
                .storagePath(storagePath)
                .createdAt(now)
                .updatedAt(now)
                .createdBy("system")
                .build();

        RulePackEntity saved = repository.save(entity);
        logger.info("Created rule pack: {} ({})", saved.getId(), saved.getVersion());

        return toDto(saved);
    }

    /**
     * Publish rule pack (DRAFT -> PUBLISHED)
     */
    public RulePackDto publishRulePack(String id) {
        RulePackEntity entity = repository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Attempted to publish non-existent rule pack: {}", id);
                    return new RuntimeException("Rule pack not found: " + id);
                });

        if (entity.getStatus() == RulePackStatus.PUBLISHED) {
            logger.warn("Attempted to publish already published rule pack: {}", id);
            throw new IllegalStateException("Rule pack is already published");
        }

        entity.setStatus(RulePackStatus.PUBLISHED);
        entity.setUpdatedAt(LocalDateTime.now());

        RulePackEntity updated = repository.save(entity);
        logger.info("Published rule pack: {} ({})", updated.getId(), updated.getVersion());

        return toDto(updated);
    }

    /**
     * Deprecate rule pack
     */
    public RulePackDto deprecateRulePack(String id) {
        RulePackEntity entity = repository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Attempted to deprecate non-existent rule pack: {}", id);
                    return new RuntimeException("Rule pack not found: " + id);
                });

        entity.setStatus(RulePackStatus.DEPRECATED);
        entity.setDeprecationDate(LocalDate.now());
        entity.setUpdatedAt(LocalDateTime.now());

        RulePackEntity updated = repository.save(entity);
        logger.info("Deprecated rule pack: {} ({})", updated.getId(), updated.getVersion());

        return toDto(updated);
    }

    /**
     * Calculate SHA256 checksum for rule pack
     */
    private String calculateChecksum(Map<String, Object> rulePackJson) {
        try {
            String jsonString = rulePackJson.toString();
            return DigestUtils.sha256Hex(jsonString);
        } catch (Exception e) {
            logger.error("Failed to calculate checksum", e);
            throw new RuntimeException("Failed to calculate checksum", e);
        }
    }

    /**
     * Convert entity to DTO
     */
    private RulePackDto toDto(RulePackEntity entity) {
        RulePackDto dto = new RulePackDto();
        dto.setId(entity.getId());
        dto.setCountry(entity.getCountryCode());
        dto.setTaxYear(entity.getTaxYear());
        dto.setVersion(entity.getVersion());
        dto.setStatus(RulePackStatus.valueOf(entity.getStatus().name()));
        dto.setEffectiveDate(entity.getEffectiveDate());
        dto.setDeprecationDate(entity.getDeprecationDate());
        dto.setChecksum(entity.getChecksum());
        dto.setStoragePath(entity.getStoragePath());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setCreatedBy(entity.getCreatedBy());

        return dto;
    }
}
