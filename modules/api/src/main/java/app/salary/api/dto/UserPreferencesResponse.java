package app.salary.api.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;

@ExcludeFromCodeCoverage
@Schema(description = "User application preferences")
public class UserPreferencesResponse {
    private NotificationPreferences notifications;
    @Schema(example = "en-US")
    private String language;
    @Schema(example = "system", description = "system | light | dark")
    private String appearance;

    @ExcludeFromCodeCoverage
    public static class NotificationPreferences {
        private Boolean push = true;
        private Boolean email = true;
        private Boolean sounds = true;
        private Boolean badges = true;

        public NotificationPreferences() {}
        public NotificationPreferences(Boolean push, Boolean email, Boolean sounds, Boolean badges) {
            this.push = push; this.email = email; this.sounds = sounds; this.badges = badges;
        }
        public Boolean getPush() { return push; }
        public void setPush(Boolean push) { this.push = push; }
        public Boolean getEmail() { return email; }
        public void setEmail(Boolean email) { this.email = email; }
        public Boolean getSounds() { return sounds; }
        public void setSounds(Boolean sounds) { this.sounds = sounds; }
        public Boolean getBadges() { return badges; }
        public void setBadges(Boolean badges) { this.badges = badges; }
    }

    public UserPreferencesResponse() {
        this.notifications = new NotificationPreferences();
        this.language = "en-US";
        this.appearance = "system";
    }

    public NotificationPreferences getNotifications() { return notifications; }
    public void setNotifications(NotificationPreferences notifications) { this.notifications = notifications; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getAppearance() { return appearance; }
    public void setAppearance(String appearance) { this.appearance = appearance; }
}
