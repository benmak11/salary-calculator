package app.salary.rulepack.repository;

import app.salary.rulepack.entity.RulePackEntity;
import app.salary.rulepack.entity.RulePackStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for rule pack entities
 */
@Repository
public interface RulePackRepository extends JpaRepository<RulePackEntity, String> {

    /**
     * Find rule packs by country, tax year, and status with pagination
     */
    Page<RulePackEntity> findByCountryCodeAndTaxYearAndStatus(
            String countryCode,
            Integer taxYear,
            RulePackStatus status,
            Pageable pageable
    );

    /**
     * Find latest published rule pack for country and tax year
     */
    @Query("SELECT r FROM RulePackEntity r " +
            "WHERE r.countryCode = :countryCode " +
            "AND r.taxYear = :taxYear " +
            "AND r.status = :status " +
            "ORDER BY r.createdAt DESC")
    Optional<RulePackEntity> findLatestPublished(
            @Param("countryCode") String countryCode,
            @Param("taxYear") Integer taxYear,
            @Param("status") RulePackStatus status
    );

    /**
     * Check if rule pack exists
     */
    boolean existsByCountryCodeAndTaxYearAndVersion(
            String countryCode,
            Integer taxYear,
            String version
    );
}
