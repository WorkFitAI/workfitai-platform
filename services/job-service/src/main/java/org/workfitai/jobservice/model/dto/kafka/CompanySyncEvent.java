package org.workfitai.jobservice.model.dto.kafka;

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
public class CompanySyncEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventType")
    private String eventType; // COMPANY_UPSERT

    @JsonProperty("timestamp")
    @Builder.Default
    private Instant timestamp = Instant.now();

    @JsonProperty("company")
    private CompanyData company;

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
