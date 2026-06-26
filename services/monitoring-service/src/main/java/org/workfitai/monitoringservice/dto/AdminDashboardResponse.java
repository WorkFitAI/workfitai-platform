package org.workfitai.monitoringservice.dto;

import org.workfitai.monitoringservice.dto.downstream.JobStatsSummary;
import org.workfitai.monitoringservice.dto.downstream.SystemStatsSummary;
import org.workfitai.monitoringservice.dto.downstream.TopSkillItem;
import org.workfitai.monitoringservice.dto.downstream.UserStatsSummary;

import java.util.List;

public record AdminDashboardResponse(
        SystemStatsSummary applicationStats,
        UserStatsSummary userStats,
        JobStatsSummary jobStats,
        List<TopSkillItem> topSkills,
        AuditStatsResponse auditStats,
        List<AuditEventResponse> recentAuditErrors
) {}
