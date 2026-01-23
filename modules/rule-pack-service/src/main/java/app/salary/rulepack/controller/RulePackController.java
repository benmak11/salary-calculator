package app.salary.rulepack.controller;

import app.salary.rulepack.dto.RulePackDto;
import app.salary.rulepack.dto.RulePackListResponse;
import app.salary.rulepack.service.RulePackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Map;

/**
 * REST API endpoints for rule pack management
 * All endpoints return JSON and support error handling
 */
@RestController
@RequestMapping("/v1/rule-packs")
@CrossOrigin(origins = "*", maxAge = 3600)
public class RulePackController {

    private static final Logger logger = LoggerFactory.getLogger(RulePackController.class);

    private final RulePackService rulePackService;

    @Autowired
    public RulePackController(RulePackService rulePackService) {
        this.rulePackService = rulePackService;
    }

    /**
     * List rule packs with filters
     * GET /v1/rule-packs?country=US&taxYear=2025&status=PUBLISHED&page=0&size=10
     */
    @GetMapping
    public ResponseEntity<RulePackListResponse> listRulePacks(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Integer taxYear,
            @RequestParam(defaultValue = "PUBLISHED") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        logger.info("Listing rule packs - country: {}, taxYear: {}, status: {}", country, taxYear, status);

        Pageable pageable = PageRequest.of(page, size);

        Page<RulePackDto> rulePacks = rulePackService.findRulePacks(
                country,
                taxYear,
                status,
                pageable
        );

        RulePackListResponse response = new RulePackListResponse(
                rulePacks.getContent(),
                rulePacks.getTotalElements(),
                page,
                size
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get latest rule pack for a country and tax year
     * GET /v1/rule-packs/latest?country=US&taxYear=2025
     */
    @GetMapping("/latest")
    public ResponseEntity<RulePackDto> getLatestRulePack(
            @RequestParam String country,
            @RequestParam Integer taxYear
    ) {
        logger.debug("Getting latest rule pack for {} {}", country, taxYear);

        return rulePackService.findLatest(country, taxYear)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get rule pack by ID
     * GET /v1/rule-packs/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<RulePackDto> getRulePack(@PathVariable String id) {
        logger.debug("Getting rule pack by id: {}", id);

        return rulePackService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Download rule pack JSON content
     * GET /v1/rule-packs/{id}/download
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Map<String, Object>> downloadRulePack(@PathVariable String id) {
        logger.info("Downloading rule pack: {}", id);

        return rulePackService.downloadRulePack(id)
                .map(json -> ResponseEntity.ok()
                        .header("Content-Type", "application/json")
                        .body(json))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create new rule pack (DRAFT status)
     * POST /v1/rule-packs
     * Body: {
     *   "country": "US",
     *   "tax_year": 2025,
     *   "version": "2025.10.0",
     *   "effective_date": "2025-01-01",
     *   "rule_pack_json": {...}
     * }
     */
    @PostMapping
    public ResponseEntity<?> createRulePack(
            @RequestParam String country,
            @RequestParam Integer taxYear,
            @RequestParam String version,
            @RequestParam String effectiveDate,
            @RequestBody Map<String, Object> rulePackJson
    ) {
        try {
            logger.info("Creating rule pack: {}-{}-{}", country, taxYear, version);

            RulePackDto created = rulePackService.createRulePack(
                    country,
                    taxYear,
                    version,
                    LocalDate.parse(effectiveDate),
                    rulePackJson
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request while creating rule pack: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating rule pack", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Publish rule pack
     * POST /v1/rule-packs/{id}/publish
     */
    @PostMapping("/{id}/publish")
    public ResponseEntity<?> publishRulePack(@PathVariable String id) {
        try {
            logger.info("Publishing rule pack: {}", id);

            RulePackDto published = rulePackService.publishRulePack(id);
            return ResponseEntity.ok(published);
        } catch (RuntimeException e) {
            logger.warn("Rule pack not found: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Deprecate rule pack
     * POST /v1/rule-packs/{id}/deprecate
     */
    @PostMapping("/{id}/deprecate")
    public ResponseEntity<?> deprecateRulePack(@PathVariable String id) {
        try {
            logger.info("Deprecating rule pack: {}", id);

            RulePackDto deprecated = rulePackService.deprecateRulePack(id);
            return ResponseEntity.ok(deprecated);
        } catch (RuntimeException e) {
            logger.warn("Rule pack not found: {}", id);
            return ResponseEntity.notFound().build();
        }
    }
}
