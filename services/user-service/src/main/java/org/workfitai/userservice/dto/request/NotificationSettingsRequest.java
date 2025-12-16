package org.workfitai.userservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingsRequest {
    private EmailNotifications email;
    private PushNotifications push;
    private SmsNotifications sms;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailNotifications {
        private Boolean jobAlerts;
        private Boolean applicationUpdates;
        private Boolean messages;
        private Boolean newsletter;
        private Boolean marketingEmails;
        private Boolean securityAlerts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PushNotifications {
        private Boolean jobAlerts;
        private Boolean applicationUpdates;
        private Boolean messages;
        private Boolean reminders;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SmsNotifications {
        private Boolean jobAlerts;
        private Boolean securityAlerts;
        private Boolean importantUpdates;
    }
}
