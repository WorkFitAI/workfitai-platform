package org.workfitai.authservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.workfitai.authservice.model.Permission;
import org.workfitai.authservice.model.Role;
import org.workfitai.authservice.repository.PermissionRepository;
import org.workfitai.authservice.repository.RoleRepository;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RolePermissionDataInitializer implements ApplicationRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    @Override
    public void run(ApplicationArguments args) {
        // 1) Ensure base permissions exist
        List<String> basePerms = List.of(
                "USER:register",
                "USER:login",
                "USER:self:read",
                "USER:self:update",
                "USER:self:refresh",
                "ADMIN:role:create",
                "ADMIN:role:grant",
                "ADMIN:perm:create"
        );

        for (String p : basePerms) {
            permissionRepository.findByName(p).orElseGet(() -> {
                log.info("[BOOTSTRAP] creating permission {}", p);
                return permissionRepository.save(Permission.builder().name(p).description(p).build());
            });
        }

        // 2) Ensure USER role exists
        roleRepository.findByName("USER").orElseGet(() -> {
            log.info("[BOOTSTRAP] creating role USER");
            return roleRepository.save(Role.builder()
                    .name("USER")
                    .description("Default user role")
                    .permissions(Set.of(
                            "USER:register",
                            "USER:login",
                            "USER:self:read",
                            "USER:self:update",
                            "USER:self:refresh"
                    ))
                    .build());
        });

        // 3) Ensure ADMIN role exists
        roleRepository.findByName("ADMIN").orElseGet(() -> {
            log.info("[BOOTSTRAP] creating role ADMIN");
            return roleRepository.save(Role.builder()
                    .name("ADMIN")
                    .description("Administrators")
                    .permissions(Set.of(
                            "USER:register",
                            "USER:login",
                            "USER:self:read",
                            "USER:self:update",
                            "USER:self:refresh",
                            "ADMIN:role:create",
                            "ADMIN:role:grant",
                            "ADMIN:perm:create"
                    ))
                    .build());
        });

        log.info("[BOOTSTRAP] role/permission seed complete");
    }
}