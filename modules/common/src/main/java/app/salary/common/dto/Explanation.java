package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;

@ExcludeFromCodeCoverage
public class Explanation {
    private String id;
    private String text;

    public Explanation() {}

    public Explanation(String id, String text) {
        this.id = id;
        this.text = text;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
