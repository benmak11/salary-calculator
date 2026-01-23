package app.salary.rulepack.dto;

import app.salary.rulepack.entity.RulePackStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RulePackDto {
    private String id;
    private String country;
    private Integer taxYear;
    private String version;
    private RulePackStatus status;
    private LocalDate effectiveDate;
    private LocalDate deprecationDate;
    private String checksum;
    private String storagePath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
}
