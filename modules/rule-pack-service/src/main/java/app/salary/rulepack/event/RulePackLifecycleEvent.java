package app.salary.rulepack.event;

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

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public Integer getTaxYear() { return taxYear; }
    public void setTaxYear(Integer taxYear) { this.taxYear = taxYear; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
}
