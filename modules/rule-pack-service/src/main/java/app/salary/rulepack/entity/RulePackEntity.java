package app.salary.rulepack.entity;

import com.google.cloud.spring.data.firestore.Document;
import org.springframework.data.annotation.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collectionName = "rule-packs")
public class RulePackEntity {

    @Id
    private String id;

    private String countryCode;
    private Integer taxYear;
    private String version;

    @Builder.Default
    private RulePackStatus status = RulePackStatus.DRAFT;

    private Date effectiveDate;    // Stored as Firestore Timestamp
    private Date deprecationDate;  // Stored as Firestore Timestamp
    private String checksum;
    private String storagePath;
    private String metadata;
    private Date createdAt;        // Stored as Firestore Timestamp
    private Date updatedAt;        // Stored as Firestore Timestamp
    private String createdBy;
    private String updatedBy;
}
