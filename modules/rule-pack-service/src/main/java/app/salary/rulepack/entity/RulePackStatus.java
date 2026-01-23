package app.salary.rulepack.entity;

import lombok.Getter;

/**
 * Rule pack lifecycle status
 */
@Getter
public enum RulePackStatus {
    DRAFT("Draft - not yet published"),
    PUBLISHED("Published - active and in use"),
    DEPRECATED("Deprecated - older version");

    private final String description;

    RulePackStatus(String description) {
        this.description = description;
    }

}
