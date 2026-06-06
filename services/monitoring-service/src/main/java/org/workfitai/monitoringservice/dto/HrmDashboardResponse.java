package org.workfitai.monitoringservice.dto;

import org.workfitai.monitoringservice.dto.downstream.HrmJobStatsSummary;
import org.workfitai.monitoringservice.dto.downstream.ManagerStatsSummary;

public record HrmDashboardResponse(
        ManagerStatsSummary applicationStats,
        HrmJobStatsSummary jobStats,
        AuditStatsResponse auditStats
) {}
