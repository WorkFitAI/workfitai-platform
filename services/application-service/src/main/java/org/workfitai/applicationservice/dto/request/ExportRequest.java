package org.workfitai.applicationservice.dto.request;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for exporting applications to CSV/Excel.
 *
 * Supports:
 * - Filtering by company, status, date range
 * - Column selection
 * - Format selection (CSV only for Phase 3)
 *
 * Limits:
 * - Max 10,000 rows per export
 * - Synchronous processing only (Phase 3)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportRequest {

    /**
     * Export format.
     * Phase 3: CSV only.
     * Phase 4/5: Excel (xlsx) support.
     */
    @NotBlank(message = "Export format is required")
    @Pattern(regexp = "csv|xlsx", message = "Format must be csv or xlsx")
    private String format;

    /**
     * Company ID to filter applications.
     * Required for company-scoped exports.
     */
    @NotBlank(message = "Company ID is required")
    private String companyId;

    /**
     * Optional status filter.
     */
    private ApplicationStatus status;

    /**
     * Optional date range filter - start date.
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant fromDate;

    /**
     * Optional date range filter - end date.
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant toDate;

    /**
     * Optional assigned HR filter.
     */
    private String assignedTo;

    /**
     * Columns to include in export.
     * If null or empty, include all columns.
     * Valid values: username, email, jobTitle, status, appliedAt, assignedTo, etc.
     */
    private List<String> columns;
}
