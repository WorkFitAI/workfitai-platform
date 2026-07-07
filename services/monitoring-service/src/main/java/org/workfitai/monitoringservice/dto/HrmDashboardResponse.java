package org.workfitai.monitoringservice.dto;

import org.workfitai.monitoringservice.dto.downstream.HrmJobStatsSummary;
import org.workfitai.monitoringservice.dto.downstream.ManagerStatsSummary;

import java.util.List;

public record HrmDashboardResponse(
        ManagerStatsSummary applicationStats,
        HrmJobStatsSummary jobStats,
        AuditStatsResponse auditStats,
        List<AuditEventResponse> recentAuditErrors
) {}
