package app.salary.rulepack.service;

import app.salary.rulepack.cache.RulePackCache;
import app.salary.rulepack.dto.RulePackDto;
import app.salary.rulepack.entity.RulePackEntity;
import app.salary.rulepack.entity.RulePackStatus;
import app.salary.rulepack.event.RulePackLifecyclePublisher;
import app.salary.rulepack.repository.RulePackRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for rule pack operations.
 *
 * Spring annotations were stripped during the Javalin migration — the service is now
 * constructed manually with its collaborators. Caching is local (Caffeine) instead of
 * Redis; Pub/Sub fan-out goes through {@link RulePackLifecyclePublisher} using the
 * native Google client.
 */
public class RulePackService {

    private static final Logger logger = LoggerFactory.getLogger(RulePackService.class);

    private final RulePackRepository repository;
    private final RulePackStorageService storageService;
    private final RulePackLifecyclePublisher lifecyclePublisher;
    private final RulePackCache cache;

    public RulePackService(RulePackRepository repository,
                           RulePackStorageService storageService,
                           RulePackLifecyclePublisher lifecyclePublisher,
                           RulePackCache cache) {
        this.repository = repository;
        this.storageService = storageService;
        this.lifecyclePublisher = lifecyclePublisher;
        this.cache = cache;
    }

    /** Pagination result kept simple — no Spring Pageable dependency. */
    public record PageResult<T>(List<T> content, long total, int page, int size) {}

    public PageResult<RulePackDto> findRulePacks(String country, Integer taxYear,
                                                 String status, int page, int size) {
        RulePackStatus rulePackStatus = RulePackStatus.valueOf(status);

        List<RulePackEntity> all = repository.findAll();

        List<RulePackDto> filtered = all.stream()
                .filter(e -> country == null || country.equals(e.getCountryCode()))
                .filter(e -> taxYear == null || taxYear.equals(e.getTaxYear()))
                .filter(e -> rulePackStatus.equals(e.getStatus()))
                .sorted(Comparator.comparing(RulePackEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toDto)
                .toList();

        long total = filtered.size();
        int fromIndex = page * size;
        if (fromIndex >= filtered.size()) {
            return new PageResult<>(List.of(), total, page, size);
        }
        int toIndex = Math.min(fromIndex + size, filtered.size());
        return new PageResult<>(filtered.subList(fromIndex, toIndex), total, page, size);
    }

    public RulePackDto findLatest(String country, Integer taxYear) {
        return cache.getOrLoad(country, taxYear, () -> loadLatestFromStore(country, taxYear)).orElse(null);
    }

    private RulePackDto loadLatestFromStore(String country, Integer taxYear) {
        logger.debug("Loading latest rule pack for {} {} from Firestore", country, taxYear);
        return repository.findAll().stream()
                .filter(e -> country.equals(e.getCountryCode()))
                .filter(e -> taxYear.equals(e.getTaxYear()))
                .filter(e -> RulePackStatus.PUBLISHED.equals(e.getStatus()))
                .max(Comparator.comparing(RulePackEntity::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(this::toDto)
                .orElse(null);
    }

    public Optional<RulePackDto> findById(String id) {
        return repository.findById(id).map(this::toDto);
    }

    public Optional<Map<String, Object>> downloadRulePack(String id) {
        return cache.getOrLoadPayload(id,
                () -> findById(id).flatMap(dto -> storageService.download(dto.getStoragePath())));
    }

    public RulePackDto createRulePack(String country, Integer taxYear, String version,
                                      LocalDate effectiveDate, Map<String, Object> rulePackJson) {
        boolean exists = repository.findAll().stream()
                .anyMatch(e -> country.equals(e.getCountryCode())
                        && taxYear.equals(e.getTaxYear())
                        && version.equals(e.getVersion()));
        if (exists) {
            logger.warn("Attempted to create duplicate rule pack: {}-{}-{}", country, taxYear, version);
            throw new IllegalArgumentException(
                    String.format("Rule pack already exists: %s-%d-%s", country, taxYear, version));
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

        RulePackEntity saved = repository.save(entity);
        logger.info("Created rule pack: {} ({})", saved.getId(), saved.getVersion());
        return toDto(saved);
    }

    public RulePackDto publishRulePack(String id) {
        RulePackEntity entity = repository.findById(id).orElseThrow(() -> {
            logger.warn("Attempted to publish non-existent rule pack: {}", id);
            return new RuntimeException("Rule pack not found: " + id);
        });

        if (entity.getStatus() == RulePackStatus.PUBLISHED) {
            logger.warn("Attempted to publish already published rule pack: {}", id);
            throw new IllegalStateException("Rule pack is already published");
        }

        entity.setStatus(RulePackStatus.PUBLISHED);
        entity.setUpdatedAt(new Date());

        RulePackEntity updated = repository.save(entity);
        cache.invalidateAll();
        logger.info("Published rule pack: {} ({})", updated.getId(), updated.getVersion());

        lifecyclePublisher.publish("RULE_PACK_PUBLISHED", updated);
        return toDto(updated);
    }

    public RulePackDto deprecateRulePack(String id) {
        RulePackEntity entity = repository.findById(id).orElseThrow(() -> {
            logger.warn("Attempted to deprecate non-existent rule pack: {}", id);
            return new RuntimeException("Rule pack not found: " + id);
        });

        entity.setStatus(RulePackStatus.DEPRECATED);
        entity.setDeprecationDate(new Date());
        entity.setUpdatedAt(new Date());

        RulePackEntity updated = repository.save(entity);
        cache.invalidateAll();
        logger.info("Deprecated rule pack: {} ({})", updated.getId(), updated.getVersion());

        lifecyclePublisher.publish("RULE_PACK_DEPRECATED", updated);
        return toDto(updated);
    }

    private String calculateChecksum(Map<String, Object> rulePackJson) {
        return DigestUtils.sha256Hex(rulePackJson.toString());
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
