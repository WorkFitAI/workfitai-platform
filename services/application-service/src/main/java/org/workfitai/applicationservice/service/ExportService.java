package org.workfitai.applicationservice.service;

import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.dto.request.ExportRequest;
import org.workfitai.applicationservice.dto.response.ExportResponse;
import org.workfitai.applicationservice.exception.BadRequestException;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.repository.ApplicationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for exporting applications to CSV/Excel.
 *
 * Phase 3: Simplified implementation
 * - CSV format only (no Excel)
 * - Synchronous processing (no async jobs)
 * - In-memory CSV generation
 * - Direct download (no MinIO pre-signed URLs)
 * - Max 10,000 rows limit
 *
 * Phase 4/5 Enhancements:
 * - Excel (xlsx) support with Apache POI
 * - Async processing with job queue
 * - MinIO storage with pre-signed URLs
 * - Auto-cleanup after 7 days
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final ApplicationRepository applicationRepository;

    private static final int MAX_EXPORT_ROWS = 10000;
    private static final List<String> DEFAULT_COLUMNS = Arrays.asList(
            "id", "username", "email", "jobId", "jobTitle", "status",
            "appliedAt", "assignedTo", "companyId");

    /**
     * Export applications to CSV.
     *
     * @param request Export request with filters
     * @return Export response with CSV data
     */
    public ExportResponse exportApplications(ExportRequest request) {
        log.info("Exporting applications for company: {}, format: {}", request.getCompanyId(), request.getFormat());

        // Validate format (CSV only for Phase 3)
        if (!"csv".equalsIgnoreCase(request.getFormat())) {
            throw new BadRequestException("Only CSV format is supported in Phase 3. Excel support coming in Phase 4.");
        }

        // Fetch applications based on filters
        List<Application> applications = fetchApplicationsForExport(request);

        // Validate row count
        if (applications.size() > MAX_EXPORT_ROWS) {
            throw new BadRequestException(
                    String.format("Export exceeds maximum limit of %d rows. Found: %d. Please add more filters.",
                            MAX_EXPORT_ROWS, applications.size()));
        }

        // Generate CSV
        String csvData = generateCSV(applications, request.getColumns());

        // Build response
        return ExportResponse.builder()
                .format("csv")
                .rowCount((long) applications.size())
                .fileSize((long) csvData.length())
                .downloadUrl("data:text/csv;charset=utf-8," + csvData) // Inline CSV (Phase 3)
                .generatedAt(Instant.now())
                .build();
    }

    /**
     * Admin-only full export (includes deleted applications)
     * Phase 4: Admin can export all platform data
     *
     * @param includeDeleted Whether to include soft-deleted applications
     * @param fromDate       Optional start date filter
     * @param toDate         Optional end date filter
     * @param columns        Columns to include
     * @return Export response with CSV data
     */
    public ExportResponse exportAllApplications(
            boolean includeDeleted,
            Instant fromDate,
            Instant toDate,
            List<String> columns) {
        log.warn("ADMIN: Full platform export requested, includeDeleted={}", includeDeleted);

        // Fetch ALL applications (no company filter)
        List<Application> applications;

        if (includeDeleted) {
            applications = applicationRepository.findAll();
        } else {
            applications = applicationRepository.findByDeletedAtIsNull();
        }

        // Apply date filters
        if (fromDate != null) {
            applications = applications.stream()
                    .filter(app -> app.getCreatedAt().isAfter(fromDate))
                    .toList();
        }

        if (toDate != null) {
            applications = applications.stream()
                    .filter(app -> app.getCreatedAt().isBefore(toDate))
                    .toList();
        }

        log.info("ADMIN: Exporting {} applications", applications.size());

        // Validate row count (higher limit for admin)
        if (applications.size() > 50000) {
            throw new BadRequestException(
                    String.format("Export exceeds maximum limit of 50,000 rows. Found: %d. Please add date filters.",
                            applications.size()));
        }

        // Generate CSV with admin-specific columns
        List<String> adminColumns = columns != null && !columns.isEmpty()
                ? columns
                : getAdminDefaultColumns();

        String csvData = generateCSV(applications, adminColumns);

        // Build response
        return ExportResponse.builder()
                .format("csv")
                .rowCount((long) applications.size())
                .fileSize((long) csvData.length())
                .downloadUrl("data:text/csv;charset=utf-8," + csvData)
                .generatedAt(Instant.now())
                .build();
    }

    /**
     * Get default columns for admin export (includes deleted fields)
     */
    private List<String> getAdminDefaultColumns() {
        return Arrays.asList(
                "id", "username", "email", "jobId", "jobTitle", "status",
                "appliedAt", "assignedTo", "companyId", "isDraft",
                "deletedAt", "deletedBy", "createdAt", "updatedAt");
    }

    /**
     * Fetch applications based on export filters.
     */
    private List<Application> fetchApplicationsForExport(ExportRequest request) {
        // Start with all company applications
        List<Application> applications = applicationRepository
                .findByCompanyIdAndDeletedAtIsNull(request.getCompanyId());

        // Apply filters
        if (request.getStatus() != null) {
            applications = applications.stream()
                    .filter(app -> app.getStatus().equals(request.getStatus()))
                    .toList();
        }

        if (request.getFromDate() != null) {
            applications = applications.stream()
                    .filter(app -> app.getCreatedAt().isAfter(request.getFromDate()))
                    .toList();
        }

        if (request.getToDate() != null) {
            applications = applications.stream()
                    .filter(app -> app.getCreatedAt().isBefore(request.getToDate()))
                    .toList();
        }

        if (request.getAssignedTo() != null) {
            applications = applications.stream()
                    .filter(app -> request.getAssignedTo().equals(app.getAssignedTo()))
                    .toList();
        }

        log.info("Filtered {} applications for export", applications.size());
        return applications;
    }

    /**
     * Generate CSV from applications.
     *
     * Phase 3: Simple CSV generation without external libraries.
     * Phase 4: Use OpenCSV or Apache Commons CSV for robust CSV generation.
     */
    private String generateCSV(List<Application> applications, List<String> requestedColumns) {
        StringWriter writer = new StringWriter();
        List<String> columns = requestedColumns != null && !requestedColumns.isEmpty()
                ? requestedColumns
                : DEFAULT_COLUMNS;

        try {
            // Write header
            writer.write(String.join(",", columns));
            writer.write("\n");

            // Write rows
            for (Application app : applications) {
                List<String> values = new ArrayList<>();

                for (String column : columns) {
                    String value = getColumnValue(app, column);
                    // Escape commas and quotes
                    value = escapeCSV(value);
                    values.add(value);
                }

                writer.write(String.join(",", values));
                writer.write("\n");
            }

        } catch (Exception e) {
            log.error("Error generating CSV", e);
            throw new RuntimeException("Failed to generate CSV", e);
        }

        return writer.toString();
    }

    /**
     * Get column value from application.
     */
    private String getColumnValue(Application app, String column) {
        return switch (column.toLowerCase()) {
            case "id" -> app.getId();
            case "username" -> app.getUsername();
            case "email" -> app.getEmail();
            case "jobid" -> app.getJobId();
            case "jobtitle" -> app.getJobSnapshot() != null ? app.getJobSnapshot().getTitle() : "";
            case "status" -> app.getStatus().name();
            case "appliedat" ->
                app.getSubmittedAt() != null ? app.getSubmittedAt().toString() : app.getCreatedAt().toString();
            case "assignedto" -> app.getAssignedTo() != null ? app.getAssignedTo() : "";
            case "companyid" -> app.getCompanyId() != null ? app.getCompanyId() : "";
            case "createdat" -> app.getCreatedAt().toString();
            case "updatedat" -> app.getUpdatedAt() != null ? app.getUpdatedAt().toString() : "";
            case "isdraft" -> String.valueOf(app.isDraft());
            case "deletedat" -> app.getDeletedAt() != null ? app.getDeletedAt().toString() : "";
            case "deletedby" -> app.getDeletedBy() != null ? app.getDeletedBy() : "";
            case "cvfileurl" -> app.getCvFileUrl() != null ? app.getCvFileUrl() : "";
            default -> "";
        };
    }

    /**
     * Escape CSV special characters and prevent formula injection.
     *
     * Formula injection attack prevention: Excel/LibreOffice auto-execute formulas
     * starting with =, +, -, @, or |. We prefix these with a single quote to
     * neutralize.
     *
     * @param value Raw value
     * @return Escaped value safe for CSV export
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }

        // Prevent formula injection - escape formula-starting characters
        if (value.length() > 0) {
            char firstChar = value.charAt(0);
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' ||
                    firstChar == '@' || firstChar == '|') {
                value = "'" + value; // Prefix with single quote to neutralize
            }
        }

        // If contains comma, quote, or newline, wrap in quotes and escape quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }

        return value;
    }
}
