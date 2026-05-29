package app.salary.rulepack.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Firestore-backed POJO for the {@code rule-packs/{id}} collection.
 *
 * Spring Data Firestore annotations were removed during the Javalin migration —
 * the repository now reads/writes through the native {@code Firestore} client,
 * which serializes any POJO with public getters/setters and a no-arg constructor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RulePackEntity {

    private String id;

    private String countryCode;
    private Integer taxYear;
    private String version;

    @Builder.Default
    private RulePackStatus status = RulePackStatus.DRAFT;

    private Date effectiveDate;     // Stored as Firestore Timestamp
    private Date deprecationDate;   // Stored as Firestore Timestamp
    private String checksum;
    private String storagePath;
    private String metadata;
    private Date createdAt;         // Stored as Firestore Timestamp
    private Date updatedAt;         // Stored as Firestore Timestamp
    private String createdBy;
    private String updatedBy;
}
