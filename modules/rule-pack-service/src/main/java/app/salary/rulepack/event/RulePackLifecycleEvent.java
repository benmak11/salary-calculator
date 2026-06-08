package app.salary.rulepack.event;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RulePackLifecycleEvent {

    private String event;
    private String country;
    private Integer taxYear;
    private String version;
    private String storagePath;

    public RulePackLifecycleEvent() {}

    public RulePackLifecycleEvent(String event, String country, Integer taxYear, String version, String storagePath) {
        this.event = event;
        this.country = country;
        this.taxYear = taxYear;
        this.version = version;
        this.storagePath = storagePath;
    }

}
