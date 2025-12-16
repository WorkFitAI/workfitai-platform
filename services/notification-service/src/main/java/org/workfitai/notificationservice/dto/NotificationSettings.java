package org.workfitai.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettings {
    private Boolean emailEnabled;
    private Boolean pushEnabled;
    private Boolean smsEnabled;

    // Default to all enabled
    public Boolean getEmailEnabled() {
        return emailEnabled != null ? emailEnabled : true;
    }

    public Boolean getPushEnabled() {
        return pushEnabled != null ? pushEnabled : true;
    }

    public Boolean getSmsEnabled() {
        return smsEnabled != null ? smsEnabled : false;
    }
}
