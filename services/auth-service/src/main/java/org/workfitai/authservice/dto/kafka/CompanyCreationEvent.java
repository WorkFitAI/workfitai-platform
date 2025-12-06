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
public class CompanyCreationEvent {
    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventType")
    @Builder.Default
    private String eventType = "COMPANY_CREATED";

    @JsonProperty("timestamp")
    @Builder.Default
    private Instant timestamp = Instant.now();

    @JsonProperty("company")
    private CompanyData company;

    @JsonProperty("hrManager")
    private HRManagerData hrManager;

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HRManagerData {
        @JsonProperty("userId")
        private String userId;
        @JsonProperty("email")
        private String email;
        @JsonProperty("username")
        private String username;
    }
}