package org.workfitai.monitoringservice.dto;

import java.util.Map;

/**
 * Aggregated statistics for the audit log dashboard stats cards.
 * Returned by GET /api/admin/audit/stats.
 */
public record AuditStatsResponse(
        long totalEvents,
        long failedEvents,
        double successRate,
        Map<String, Long> byService,
        Map<String, Long> byAction,
        Map<String, Long> byActor
) {}
