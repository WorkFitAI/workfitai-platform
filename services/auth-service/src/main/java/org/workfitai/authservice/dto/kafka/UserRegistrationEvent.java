package org.workfitai.authservice.dto.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventType")
    private String eventType; // "USER_REGISTERED"

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("userData")
    private UserData userData;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserData {
        @JsonProperty("userId")
        private String userId;

        @JsonProperty("username")
        private String username;

        @JsonProperty("fullName")
        private String fullName;

        @JsonProperty("email")
        private String email;

        @JsonProperty("phoneNumber")
        private String phoneNumber;

        @JsonProperty("passwordHash")
        private String passwordHash;

        @JsonProperty("role")
        private String role;

        @JsonProperty("status")
        private String status;

        @JsonProperty("hrProfile")
        private HrProfile hrProfile;

        @JsonProperty("company")
        private CompanyData company;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HrProfile {
        @JsonProperty("department")
        private String department;

        @JsonProperty("hrManagerEmail")
        private String hrManagerEmail;

        @JsonProperty("address")
        private String address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyData {
        @JsonProperty("companyId")
        private String companyId;

        @JsonProperty("name")
        private String name;

        @JsonProperty("logoUrl")
        private String logoUrl;

        @JsonProperty("websiteUrl")
        private String websiteUrl;

        @JsonProperty("description")
        private String description;

        @JsonProperty("address")
        private String address;

        @JsonProperty("size")
        private String size;
    }
}
