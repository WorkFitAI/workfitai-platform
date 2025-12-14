package org.workfitai.applicationservice.dto.request;

import org.workfitai.applicationservice.model.enums.ApplicationStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Admin request to override any application field
 * Bypasses all validation and business rules
 * USE WITH EXTREME CAUTION
 */
public record AdminOverrideRequest(
    ApplicationStatus status, // Override status

    String assignedTo, // Override assignment

    String cvFileUrl, // Override CV file

    String companyId, // Override company

    Boolean isDraft, // Override draft flag

    Instant submittedAt, // Override submission time

    Instant updatedAt, // Override update time

    String deletedBy, // Override deletion info

    Instant deletedAt, // Override deletion time

    Map<String, Object> customFields, // Any other fields

    String reason // REQUIRED: Reason for override (audit trail)
) {}
