package org.workfitai.authservice.config;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.workfitai.authservice.enums.UserRole;
import org.workfitai.authservice.model.Permission;
import org.workfitai.authservice.model.Role;
import org.workfitai.authservice.repository.PermissionRepository;
import org.workfitai.authservice.repository.RoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Initializes roles and permissions for the WorkFitAI platform.
 * 
 * Permission naming convention: {resource}:{action}
 * Resources: user, candidate, hr, admin, job, company, skill, cv, application
 * Actions: create, read, update, delete, list, search, manage
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RolePermissionDataInitializer implements ApplicationRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[BOOTSTRAP] Starting role/permission initialization...");

        // 1) Create all permissions
        Map<String, String> allPermissions = buildPermissionsMap();
        for (Map.Entry<String, String> entry : allPermissions.entrySet()) {
            createPermissionIfNotExists(entry.getKey(), entry.getValue());
        }
        log.info("[BOOTSTRAP] Created {} permissions", allPermissions.size());

        // 2) Create roles with their permissions
        createCandidateRole();
        createHRRole();
        createHRManagerRole();
        createAdminRole();

        log.info("[BOOTSTRAP] Role/permission seed complete");
    }

    /**
     * Build a comprehensive map of all permissions with descriptions.
     */
    private Map<String, String> buildPermissionsMap() {
        Map<String, String> perms = new LinkedHashMap<>();

        // ==================== AUTH SERVICE ====================
        // Authentication (public)
        perms.put("auth:register", "Register a new account");
        perms.put("auth:login", "Login to the system");
        perms.put("auth:logout", "Logout from the system");
        perms.put("auth:refresh", "Refresh access token");

        // Profile management
        perms.put("profile:read", "Read own profile");
        perms.put("profile:update", "Update own profile");

        // Role management (admin only)
        perms.put("role:create", "Create new roles");
        perms.put("role:read", "View roles");
        perms.put("role:update", "Update roles");
        perms.put("role:delete", "Delete roles");
        perms.put("role:grant", "Grant roles to users");
        perms.put("role:revoke", "Revoke roles from users");

        // Permission management (admin only)
        perms.put("perm:create", "Create new permissions");
        perms.put("perm:read", "View permissions");
        perms.put("perm:update", "Update permissions");
        perms.put("perm:delete", "Delete permissions");

        // ==================== USER SERVICE ====================
        // Candidate management
        perms.put("candidate:create", "Create candidate profiles");
        perms.put("candidate:read", "View candidate profiles");
        perms.put("candidate:update", "Update candidate profiles");
        perms.put("candidate:delete", "Delete candidate profiles");
        perms.put("candidate:list", "List all candidates");
        perms.put("candidate:search", "Search candidates");
        perms.put("candidate:stats", "View candidate statistics");

        // Candidate skills
        perms.put("candidate:skill:add", "Add skills to candidate profile");
        perms.put("candidate:skill:remove", "Remove skills from candidate profile");
        perms.put("candidate:skill:read", "View candidate skills");

        // HR management
        perms.put("hr:create", "Create HR profiles");
        perms.put("hr:read", "View HR profiles");
        perms.put("hr:update", "Update HR profiles");
        perms.put("hr:delete", "Delete HR profiles");
        perms.put("hr:list", "List all HR users");
        perms.put("hr:search", "Search HR users");
        perms.put("hr:stats", "View HR statistics");

        // Admin user management
        perms.put("admin:create", "Create admin profiles");
        perms.put("admin:read", "View admin profiles");
        perms.put("admin:update", "Update admin profiles");
        perms.put("admin:delete", "Delete admin profiles");
        perms.put("admin:list", "List all admins");
        perms.put("admin:search", "Search admins");

        // ==================== JOB SERVICE ====================
        // Job management
        perms.put("job:create", "Create job postings");
        perms.put("job:read", "View job details");
        perms.put("job:update", "Update job postings");
        perms.put("job:delete", "Delete job postings");
        perms.put("job:list", "List all jobs");
        perms.put("job:search", "Search jobs");
        perms.put("job:publish", "Publish job postings");
        perms.put("job:unpublish", "Unpublish job postings");
        perms.put("job:stats", "View job statistics");

        // Company management
        perms.put("company:create", "Create companies");
        perms.put("company:read", "View company details");
        perms.put("company:update", "Update companies");
        perms.put("company:delete", "Delete companies");
        perms.put("company:list", "List all companies");
        perms.put("company:search", "Search companies");
        perms.put("company:verify", "Verify companies");

        // Skill management
        perms.put("skill:create", "Create skills");
        perms.put("skill:read", "View skills");
        perms.put("skill:update", "Update skills");
        perms.put("skill:delete", "Delete skills");
        perms.put("skill:list", "List all skills");
        perms.put("skill:search", "Search skills");

        // ==================== CV SERVICE ====================
        // CV management
        perms.put("cv:create", "Create/upload CVs");
        perms.put("cv:read", "View CV details");
        perms.put("cv:update", "Update CVs");
        perms.put("cv:delete", "Delete CVs");
        perms.put("cv:list", "List CVs");
        perms.put("cv:download", "Download CVs");
        perms.put("cv:parse", "Parse CV content");
        perms.put("cv:analyze", "Analyze CV with AI");

        // ==================== APPLICATION SERVICE ====================
        // Application management
        perms.put("application:create", "Submit job applications");
        perms.put("application:read", "View application details");
        perms.put("application:update", "Update applications");
        perms.put("application:delete", "Delete/withdraw applications");
        perms.put("application:list", "List applications");
        perms.put("application:search", "Search applications");

        // Application status management
        perms.put("application:review", "Review applications");
        perms.put("application:shortlist", "Shortlist applications");
        perms.put("application:reject", "Reject applications");
        perms.put("application:approve", "Approve applications");
        perms.put("application:stats", "View application statistics");

        // Application advanced management (HR_MANAGER)
        perms.put("application:note", "Add notes to applications");
        perms.put("application:assign", "Assign applications to HR users");
        perms.put("application:export", "Export applications to CSV/Excel");
        perms.put("application:manage", "Access manager dashboard and company statistics");

        // Interview scheduling
        perms.put("interview:create", "Schedule interviews");
        perms.put("interview:read", "View interview details");
        perms.put("interview:update", "Update interviews");
        perms.put("interview:delete", "Cancel interviews");
        perms.put("interview:feedback", "Provide interview feedback");

        // ==================== SYSTEM/MONITORING ====================
        perms.put("system:health", "View system health");
        perms.put("system:metrics", "View system metrics");
        perms.put("system:logs", "View system logs");
        perms.put("system:config", "Manage system configuration");

        return perms;
    }

    /**
     * Create a permission if it doesn't exist.
     */
    private void createPermissionIfNotExists(String name, String description) {
        permissionRepository.findByName(name).orElseGet(() -> {
            log.debug("[BOOTSTRAP] Creating permission: {}", name);
            return permissionRepository.save(Permission.builder()
                    .name(name)
                    .description(description)
                    .build());
        });
    }

    /**
     * Create CANDIDATE role with appropriate permissions.
     * Candidates can manage their own profile, CV, and applications.
     */
    private void createCandidateRole() {
        roleRepository.findByName(UserRole.CANDIDATE.getRoleName()).orElseGet(() -> {
            log.info("[BOOTSTRAP] Creating role: CANDIDATE");
            Set<String> permissions = new HashSet<>();

            // Auth permissions
            permissions.add("auth:login");
            permissions.add("auth:logout");
            permissions.add("auth:refresh");

            // Profile permissions (own profile only)
            permissions.add("profile:read");
            permissions.add("profile:update");

            // Candidate self-management
            permissions.add("candidate:read");
            permissions.add("candidate:update");
            permissions.add("candidate:skill:add");
            permissions.add("candidate:skill:remove");
            permissions.add("candidate:skill:read");

            // Job browsing (read-only)
            permissions.add("job:read");
            permissions.add("job:list");
            permissions.add("job:search");

            // Company browsing (read-only)
            permissions.add("company:read");
            permissions.add("company:list");
            permissions.add("company:search");

            // Skill browsing
            permissions.add("skill:read");
            permissions.add("skill:list");
            permissions.add("skill:search");

            // CV management (own CVs)
            permissions.add("cv:create");
            permissions.add("cv:read");
            permissions.add("cv:update");
            permissions.add("cv:delete");
            permissions.add("cv:list");
            permissions.add("cv:download");
            permissions.add("cv:parse");

            // Application management (own applications)
            permissions.add("application:create");
            permissions.add("application:read");
            permissions.add("application:update");
            permissions.add("application:delete");
            permissions.add("application:list");

            // Interview (own interviews - read only)
            permissions.add("interview:read");

            return roleRepository.save(Role.builder()
                    .name(UserRole.CANDIDATE.getRoleName())
                    .description("Job seekers who can manage their profile, CV, and applications")
                    .permissions(permissions)
                    .build());
        });
    }

    /**
     * Create HR role with appropriate permissions.
     * HR users can manage jobs, review applications, and conduct interviews.
     */
    private void createHRRole() {
        roleRepository.findByName(UserRole.HR.getRoleName()).orElseGet(() -> {
            log.info("[BOOTSTRAP] Creating role: HR");
            Set<String> permissions = new HashSet<>();

            // Auth permissions
            permissions.add("auth:login");
            permissions.add("auth:logout");
            permissions.add("auth:refresh");

            // Profile permissions
            permissions.add("profile:read");
            permissions.add("profile:update");

            // HR self-management
            permissions.add("hr:read");
            permissions.add("hr:update");

            // Candidate viewing (for recruitment)
            permissions.add("candidate:read");
            permissions.add("candidate:list");
            permissions.add("candidate:search");
            permissions.add("candidate:stats");
            permissions.add("candidate:skill:read");

            // Job management (full CRUD)
            permissions.add("job:create");
            permissions.add("job:read");
            permissions.add("job:update");
            permissions.add("job:delete");
            permissions.add("job:list");
            permissions.add("job:search");
            permissions.add("job:publish");
            permissions.add("job:unpublish");
            permissions.add("job:stats");

            // Company management (own company)
            permissions.add("company:read");
            permissions.add("company:update");
            permissions.add("company:list");
            permissions.add("company:search");

            // Skill management
            permissions.add("skill:create");
            permissions.add("skill:read");
            permissions.add("skill:update");
            permissions.add("skill:list");
            permissions.add("skill:search");

            // CV viewing (for candidate review)
            permissions.add("cv:read");
            permissions.add("cv:list");
            permissions.add("cv:download");
            permissions.add("cv:analyze");

            // Application management (full access)
            permissions.add("application:read");
            permissions.add("application:update");
            permissions.add("application:list");
            permissions.add("application:search");
            permissions.add("application:review");
            permissions.add("application:shortlist");
            permissions.add("application:reject");
            permissions.add("application:approve");
            permissions.add("application:stats");

            // Interview management (full CRUD)
            permissions.add("interview:create");
            permissions.add("interview:read");
            permissions.add("interview:update");
            permissions.add("interview:delete");
            permissions.add("interview:feedback");

            return roleRepository.save(Role.builder()
                    .name(UserRole.HR.getRoleName())
                    .description("HR managers who can post jobs, review applications, and conduct interviews")
                    .permissions(permissions)
                    .build());
        });
    }

    /**
     * Create HR_MANAGER role with company-level management capabilities.
     * HR Managers have all HR permissions plus:
     * - Company-wide application management
     * - Team coordination (assign applications)
     * - Advanced analytics and reporting
     * - Data export capabilities
     */
    private void createHRManagerRole() {
        roleRepository.findByName(UserRole.HR_MANAGER.getRoleName()).orElseGet(() -> {
            log.info("[BOOTSTRAP] Creating role: HR_MANAGER");
            Set<String> permissions = new HashSet<>();

            // Auth permissions
            permissions.add("auth:login");
            permissions.add("auth:logout");
            permissions.add("auth:refresh");

            // Profile permissions
            permissions.add("profile:read");
            permissions.add("profile:update");

            // HR self-management
            permissions.add("hr:read");
            permissions.add("hr:update");
            permissions.add("hr:list"); // Can view HR team members
            permissions.add("hr:search"); // Can search HR team

            // Candidate viewing (for recruitment)
            permissions.add("candidate:read");
            permissions.add("candidate:list");
            permissions.add("candidate:search");
            permissions.add("candidate:stats");
            permissions.add("candidate:skill:read");

            // Job management (full CRUD)
            permissions.add("job:create");
            permissions.add("job:read");
            permissions.add("job:update");
            permissions.add("job:delete");
            permissions.add("job:list");
            permissions.add("job:search");
            permissions.add("job:publish");
            permissions.add("job:unpublish");
            permissions.add("job:stats");

            // Company management (own company - full control)
            permissions.add("company:create"); // Can create company during registration
            permissions.add("company:read");
            permissions.add("company:update");
            permissions.add("company:list");
            permissions.add("company:search");

            // Skill management
            permissions.add("skill:create");
            permissions.add("skill:read");
            permissions.add("skill:update");
            permissions.add("skill:delete"); // Can remove obsolete skills
            permissions.add("skill:list");
            permissions.add("skill:search");

            // CV viewing (for candidate review)
            permissions.add("cv:read");
            permissions.add("cv:list");
            permissions.add("cv:download");
            permissions.add("cv:analyze");

            // Application management (full access + advanced features)
            permissions.add("application:read");
            permissions.add("application:update");
            permissions.add("application:list");
            permissions.add("application:search");
            permissions.add("application:review");
            permissions.add("application:shortlist");
            permissions.add("application:reject");
            permissions.add("application:approve");
            permissions.add("application:stats");

            // HR_MANAGER exclusive permissions
            permissions.add("application:note"); // Add internal/public notes
            permissions.add("application:assign"); // Assign apps to HR team
            permissions.add("application:export"); // Export to CSV/Excel
            permissions.add("application:manage"); // Manager dashboard & company stats

            // Interview management (full CRUD)
            permissions.add("interview:create");
            permissions.add("interview:read");
            permissions.add("interview:update");
            permissions.add("interview:delete");
            permissions.add("interview:feedback");

            return roleRepository.save(Role.builder()
                    .name(UserRole.HR_MANAGER.getRoleName())
                    .description(
                            "HR managers who lead recruitment teams, manage company jobs, and coordinate hiring processes")
                    .permissions(permissions)
                    .build());
        });
    }

    /**
     * Create ADMIN role with full system access.
     * Admins have complete control over all resources.
     */
    private void createAdminRole() {
        roleRepository.findByName(UserRole.ADMIN.getRoleName()).orElseGet(() -> {
            log.info("[BOOTSTRAP] Creating role: ADMIN");
            Set<String> permissions = new HashSet<>();

            // Auth permissions
            permissions.add("auth:register");
            permissions.add("auth:login");
            permissions.add("auth:logout");
            permissions.add("auth:refresh");

            // Profile permissions
            permissions.add("profile:read");
            permissions.add("profile:update");

            // Role management (full CRUD)
            permissions.add("role:create");
            permissions.add("role:read");
            permissions.add("role:update");
            permissions.add("role:delete");
            permissions.add("role:grant");
            permissions.add("role:revoke");

            // Permission management (full CRUD)
            permissions.add("perm:create");
            permissions.add("perm:read");
            permissions.add("perm:update");
            permissions.add("perm:delete");

            // User management - Candidates
            permissions.add("candidate:create");
            permissions.add("candidate:read");
            permissions.add("candidate:update");
            permissions.add("candidate:delete");
            permissions.add("candidate:list");
            permissions.add("candidate:search");
            permissions.add("candidate:stats");
            permissions.add("candidate:skill:add");
            permissions.add("candidate:skill:remove");
            permissions.add("candidate:skill:read");

            // User management - HR
            permissions.add("hr:create");
            permissions.add("hr:read");
            permissions.add("hr:update");
            permissions.add("hr:delete");
            permissions.add("hr:list");
            permissions.add("hr:search");
            permissions.add("hr:stats");

            // User management - Admins
            permissions.add("admin:create");
            permissions.add("admin:read");
            permissions.add("admin:update");
            permissions.add("admin:delete");
            permissions.add("admin:list");
            permissions.add("admin:search");

            // Job management (full CRUD)
            permissions.add("job:create");
            permissions.add("job:read");
            permissions.add("job:update");
            permissions.add("job:delete");
            permissions.add("job:list");
            permissions.add("job:search");
            permissions.add("job:publish");
            permissions.add("job:unpublish");
            permissions.add("job:stats");

            // Company management (full CRUD)
            permissions.add("company:create");
            permissions.add("company:read");
            permissions.add("company:update");
            permissions.add("company:delete");
            permissions.add("company:list");
            permissions.add("company:search");
            permissions.add("company:verify");

            // Skill management (full CRUD)
            permissions.add("skill:create");
            permissions.add("skill:read");
            permissions.add("skill:update");
            permissions.add("skill:delete");
            permissions.add("skill:list");
            permissions.add("skill:search");

            // CV management (full access)
            permissions.add("cv:create");
            permissions.add("cv:read");
            permissions.add("cv:update");
            permissions.add("cv:delete");
            permissions.add("cv:list");
            permissions.add("cv:download");
            permissions.add("cv:parse");
            permissions.add("cv:analyze");

            // Application management (full access)
            permissions.add("application:create");
            permissions.add("application:read");
            permissions.add("application:update");
            permissions.add("application:delete");
            permissions.add("application:list");
            permissions.add("application:search");
            permissions.add("application:review");
            permissions.add("application:shortlist");
            permissions.add("application:reject");
            permissions.add("application:approve");
            permissions.add("application:stats");
            permissions.add("application:note");
            permissions.add("application:assign");
            permissions.add("application:export");
            permissions.add("application:manage");

            // Interview management (full CRUD)
            permissions.add("interview:create");
            permissions.add("interview:read");
            permissions.add("interview:update");
            permissions.add("interview:delete");
            permissions.add("interview:feedback");

            // System management
            permissions.add("system:health");
            permissions.add("system:metrics");
            permissions.add("system:logs");
            permissions.add("system:config");

            return roleRepository.save(Role.builder()
                    .name(UserRole.ADMIN.getRoleName())
                    .description("System administrators with full access to all resources and configurations")
                    .permissions(permissions)
                    .build());
        });
    }
}