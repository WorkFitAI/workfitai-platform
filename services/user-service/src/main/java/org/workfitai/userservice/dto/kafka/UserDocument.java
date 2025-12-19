package org.workfitai.userservice.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Elasticsearch document for user index.
 * Stored in "users-index" for fast search and filtering.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDocument {

    private UUID userId;
    private String username;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String avatarUrl;
    private String role; // Store as String for Elasticsearch
    private String status; // Store as String for Elasticsearch
    private boolean blocked;
    private boolean deleted;

    // Company information (for HR users)
    private String companyNo;
    private String companyName;

    private Instant createdDate;
    private Instant lastModifiedDate;

    /**
     * Version for optimistic locking
     */
    private Long version;
}
