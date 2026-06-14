package org.workfitai.authservice.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.workfitai.authservice.model.Permission;
import org.workfitai.authservice.model.Role;
import org.workfitai.authservice.service.AuthAuditService;

import java.util.List;

/**
 * AOP aspect for auditing RBAC management operations (permission/role CRUD).
 * Fires on successful execution; failures are silently swallowed to prevent audit noise propagation.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RbacAuditAspect {

    private final AuthAuditService authAuditService;

    // ─── Permission pointcuts ─────────────────────────────────────────────────

    @Pointcut("execution(* org.workfitai.authservice.service.impl.PermissionServiceImpl.create(..))")
    public void permissionCreatePointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.PermissionServiceImpl.createBatch(..))")
    public void permissionCreateBatchPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.PermissionServiceImpl.updateDescription(..))")
    public void permissionUpdatePointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.PermissionServiceImpl.deleteByName(..))")
    public void permissionDeletePointcut() {}

    // ─── Role pointcuts ───────────────────────────────────────────────────────

    @Pointcut("execution(* org.workfitai.authservice.service.impl.RoleServiceImpl.create(..))")
    public void roleCreatePointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.RoleServiceImpl.createBatch(..))")
    public void roleCreateBatchPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.RoleServiceImpl.updateDescription(..))")
    public void roleUpdatePointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.RoleServiceImpl.deleteByName(..))")
    public void roleDeletePointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.RoleServiceImpl.cloneRole(..))")
    public void roleClonePointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.RoleServiceImpl.addPermission(..))")
    public void roleAddPermissionPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.RoleServiceImpl.removePermission(..))")
    public void roleRemovePermissionPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.RoleServiceImpl.addPermissions(..))")
    public void roleAddPermissionsPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.RoleServiceImpl.removePermissions(..))")
    public void roleRemovePermissionsPointcut() {}

    // ─── Permission advice ────────────────────────────────────────────────────

    @AfterReturning(pointcut = "permissionCreatePointcut()", returning = "result")
    public void logPermissionCreated(Object result) {
        try {
            if (result instanceof Permission perm) {
                authAuditService.logPermissionCreated(perm.getName());
            }
        } catch (Exception e) {
            log.error("Audit error on permissionCreatePointcut", e);
        }
    }

    @AfterReturning(pointcut = "permissionCreateBatchPointcut()", returning = "result")
    public void logPermissionBatchCreated(Object result) {
        try {
            if (result instanceof List<?> list) {
                authAuditService.logPermissionBatchCreated(list.size());
            }
        } catch (Exception e) {
            log.error("Audit error on permissionCreateBatchPointcut", e);
        }
    }

    @AfterReturning("permissionUpdatePointcut()")
    public void logPermissionUpdated(JoinPoint jp) {
        try {
            String name = (String) jp.getArgs()[0];
            authAuditService.logPermissionUpdated(name);
        } catch (Exception e) {
            log.error("Audit error on permissionUpdatePointcut", e);
        }
    }

    @AfterReturning("permissionDeletePointcut()")
    public void logPermissionDeleted(JoinPoint jp) {
        try {
            String name = (String) jp.getArgs()[0];
            authAuditService.logPermissionDeleted(name);
        } catch (Exception e) {
            log.error("Audit error on permissionDeletePointcut", e);
        }
    }

    // ─── Role advice ──────────────────────────────────────────────────────────

    @AfterReturning(pointcut = "roleCreatePointcut()", returning = "result")
    public void logRoleCreated(Object result) {
        try {
            if (result instanceof Role role) {
                authAuditService.logRoleCreated(role.getName());
            }
        } catch (Exception e) {
            log.error("Audit error on roleCreatePointcut", e);
        }
    }

    @AfterReturning(pointcut = "roleCreateBatchPointcut()", returning = "result")
    public void logRoleBatchCreated(Object result) {
        try {
            if (result instanceof List<?> list) {
                authAuditService.logRoleBatchCreated(list.size());
            }
        } catch (Exception e) {
            log.error("Audit error on roleCreateBatchPointcut", e);
        }
    }

    @AfterReturning("roleUpdatePointcut()")
    public void logRoleUpdated(JoinPoint jp) {
        try {
            String name = (String) jp.getArgs()[0];
            authAuditService.logRoleUpdated(name);
        } catch (Exception e) {
            log.error("Audit error on roleUpdatePointcut", e);
        }
    }

    @AfterReturning("roleDeletePointcut()")
    public void logRoleDeleted(JoinPoint jp) {
        try {
            String name = (String) jp.getArgs()[0];
            authAuditService.logRoleDeleted(name);
        } catch (Exception e) {
            log.error("Audit error on roleDeletePointcut", e);
        }
    }

    @AfterReturning(pointcut = "roleClonePointcut()", returning = "result")
    public void logRoleCloned(JoinPoint jp, Object result) {
        try {
            String sourceName = (String) jp.getArgs()[0];
            String newName = result instanceof Role role ? role.getName() : (String) jp.getArgs()[1];
            authAuditService.logRoleCloned(sourceName, newName);
        } catch (Exception e) {
            log.error("Audit error on roleClonePointcut", e);
        }
    }

    @AfterReturning("roleAddPermissionPointcut()")
    public void logRolePermissionAdded(JoinPoint jp) {
        try {
            String roleName = (String) jp.getArgs()[0];
            String permName = (String) jp.getArgs()[1];
            authAuditService.logRolePermissionAdded(roleName, permName);
        } catch (Exception e) {
            log.error("Audit error on roleAddPermissionPointcut", e);
        }
    }

    @AfterReturning("roleRemovePermissionPointcut()")
    public void logRolePermissionRemoved(JoinPoint jp) {
        try {
            String roleName = (String) jp.getArgs()[0];
            String permName = (String) jp.getArgs()[1];
            authAuditService.logRolePermissionRemoved(roleName, permName);
        } catch (Exception e) {
            log.error("Audit error on roleRemovePermissionPointcut", e);
        }
    }

    @AfterReturning("roleAddPermissionsPointcut()")
    public void logRolePermissionsAdded(JoinPoint jp) {
        try {
            String roleName = (String) jp.getArgs()[0];
            int count = jp.getArgs()[1] instanceof List<?> list ? list.size() : 0;
            authAuditService.logRolePermissionsAdded(roleName, count);
        } catch (Exception e) {
            log.error("Audit error on roleAddPermissionsPointcut", e);
        }
    }

    @AfterReturning("roleRemovePermissionsPointcut()")
    public void logRolePermissionsRemoved(JoinPoint jp) {
        try {
            String roleName = (String) jp.getArgs()[0];
            int count = jp.getArgs()[1] instanceof List<?> list ? list.size() : 0;
            authAuditService.logRolePermissionsRemoved(roleName, count);
        } catch (Exception e) {
            log.error("Audit error on roleRemovePermissionsPointcut", e);
        }
    }
}
