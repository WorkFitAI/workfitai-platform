package org.workfitai.applicationservice.service;

import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.dto.request.ExportRequest;
import org.workfitai.applicationservice.dto.response.ExportResponse;
import org.workfitai.applicationservice.exception.BadRequestException;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.repository.ApplicationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Service for exporting applications to CSV.
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final ApplicationRepository applicationRepository;
    private final MongoTemplate mongoTemplate;

    private static final int MAX_EXPORT_ROWS = 10000;
    private static final int ADMIN_EXPORT_LIMIT = 50000;
    private static final List<String> DEFAULT_COLUMNS = Arrays.asList(
            "id", "username", "email", "jobId", "jobTitle", "status",
            "appliedAt", "assignedTo", "companyId");

    public ExportResponse exportApplications(ExportRequest request) {
        log.info("Exporting applications for company: {}, format: {}", request.getCompanyId(), request.getFormat());

        if (!"csv".equalsIgnoreCase(request.getFormat())) {
            throw new BadRequestException("Only CSV format is supported. Excel support coming in Phase 4.");
        }

        List<Application> applications = fetchApplicationsForExport(request);

        if (applications.size() > MAX_EXPORT_ROWS) {
            throw new BadRequestException(
                    String.format("Export exceeds maximum limit of %d rows. Found: %d. Please add more filters.",
                            MAX_EXPORT_ROWS, applications.size()));
        }

        String csvData = generateCSV(applications, request.getColumns());

        return ExportResponse.builder()
                .format("csv")
                .rowCount((long) applications.size())
                .fileSize((long) csvData.length())
                .downloadUrl("data:text/csv;charset=utf-8," + csvData)
                .generatedAt(Instant.now())
                .build();
    }

    public ExportResponse exportAllApplications(
            boolean includeDeleted, Instant fromDate, Instant toDate, List<String> columns) {
        log.warn("ADMIN: Full platform export requested, includeDeleted={}", includeDeleted);

        // Push all filtering into MongoDB to avoid OOM on large datasets (M1 fix)
        Criteria criteria = includeDeleted ? new Criteria() : Criteria.where("deletedAt").isNull();
        if (fromDate != null) {
            criteria.and("createdAt").gte(fromDate);
        }
        if (toDate != null) {
            criteria.and("createdAt").lte(toDate);
        }

        // Fetch one extra to detect limit breach before CSV generation
        List<Application> applications = mongoTemplate.find(
                Query.query(criteria).limit(ADMIN_EXPORT_LIMIT + 1), Application.class);

        if (applications.size() > ADMIN_EXPORT_LIMIT) {
            throw new BadRequestException(
                    String.format("Export exceeds maximum limit of %,d rows. Found: %d+. Please add date filters.",
                            ADMIN_EXPORT_LIMIT, ADMIN_EXPORT_LIMIT));
        }

        List<String> adminColumns = (columns != null && !columns.isEmpty()) ? columns : getAdminDefaultColumns();
        String csvData = generateCSV(applications, adminColumns);

        return ExportResponse.builder()
                .format("csv")
                .rowCount((long) applications.size())
                .fileSize((long) csvData.length())
                .downloadUrl("data:text/csv;charset=utf-8," + csvData)
                .generatedAt(Instant.now())
                .build();
    }

    private List<String> getAdminDefaultColumns() {
        return Arrays.asList(
                "id", "username", "email", "jobId", "jobTitle", "status",
                "appliedAt", "assignedTo", "companyId",
                "deletedAt", "deletedBy", "createdAt", "updatedAt");
    }

    private List<Application> fetchApplicationsForExport(ExportRequest request) {
        Criteria criteria = Criteria.where("companyId").is(request.getCompanyId())
                .and("deletedAt").isNull();

        if (request.getStatus() != null) {
            criteria.and("status").is(request.getStatus());
        }
        if (request.getFromDate() != null) {
            criteria.and("createdAt").gte(request.getFromDate());
        }
        if (request.getToDate() != null) {
            criteria.and("createdAt").lte(request.getToDate());
        }
        if (request.getAssignedTo() != null) {
            criteria.and("assignedTo").is(request.getAssignedTo());
        }

        // Fetch one extra to detect if we exceed the limit before CSV generation
        Query query = Query.query(criteria).limit(MAX_EXPORT_ROWS + 1);
        List<Application> applications = mongoTemplate.find(query, Application.class);

        log.info("Fetched {} applications for export", applications.size());
        return applications;
    }

    private String generateCSV(List<Application> applications, List<String> requestedColumns) {
        StringWriter writer = new StringWriter();
        List<String> columns = (requestedColumns != null && !requestedColumns.isEmpty())
                ? requestedColumns
                : DEFAULT_COLUMNS;

        try {
            writer.write(String.join(",", columns));
            writer.write("\n");

            for (Application app : applications) {
                List<String> values = new ArrayList<>();
                for (String column : columns) {
                    values.add(escapeCSV(getColumnValue(app, column)));
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

    private String getColumnValue(Application app, String column) {
        return switch (column.toLowerCase()) {
            case "id" -> app.getId();
            case "username" -> app.getUsername();
            case "email" -> app.getEmail();
            case "jobid" -> app.getJobId();
            case "jobtitle" -> app.getJobSnapshot() != null ? app.getJobSnapshot().getTitle() : "";
            case "status" -> app.getStatus().name();
            case "appliedat" -> app.getCreatedAt().toString();
            case "assignedto" -> app.getAssignedTo() != null ? app.getAssignedTo() : "";
            case "companyid" -> app.getCompanyId() != null ? app.getCompanyId() : "";
            case "createdat" -> app.getCreatedAt().toString();
            case "updatedat" -> app.getUpdatedAt() != null ? app.getUpdatedAt().toString() : "";
            case "deletedat" -> app.getDeletedAt() != null ? app.getDeletedAt().toString() : "";
            case "deletedby" -> app.getDeletedBy() != null ? app.getDeletedBy() : "";
            case "cvfileurl" -> app.getCvFileUrl() != null ? app.getCvFileUrl() : "";
            default -> "";
        };
    }

    // Leading =, +, -, @, |, \t trigger formula execution in spreadsheet apps (CSV injection).
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }

        if (!value.isEmpty()) {
            char first = value.charAt(0);
            if (first == '=' || first == '+' || first == '-' ||
                    first == '@' || first == '|' || first == '\t') {
                value = "'" + value;
            }
        }

        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }

        return value;
    }
}
