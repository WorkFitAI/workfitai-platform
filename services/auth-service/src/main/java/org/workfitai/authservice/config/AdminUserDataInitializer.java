package org.workfitai.authservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.workfitai.authservice.enums.UserStatus;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;

import java.util.Set;

/**
 * Initializes admin/HR users in MongoDB.
 * These users have matching UUIDs with user-service PostgreSQL for
 * cross-service consistency.
 * 
 * Synchronization strategy:
 * 1. Seed data: Uses identical UUIDs in both auth-service (MongoDB) and
 * user-service (PostgreSQL)
 * 2. Runtime sync: Kafka events ensure status changes are propagated between
 * services
 * - user-service publishes approval events -> auth-service updates status
 * - auth-service publishes registration events -> user-service creates profiles
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2) // Run after RolePermissionDataInitializer
public class AdminUserDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        // 1) Create ADMIN user (password: admin123)
        createUserIfNotExists(
                "00000000-0000-0000-0000-000000000001",
                "admin",
                "admin@workfitai.com",
                passwordEncoder.encode("admin123"),
                Set.of("ADMIN"),
                UserStatus.ACTIVE,
                null);

        // 2) Create HR_MANAGER user (password: hrmanager123)
        createUserIfNotExists(
                "00000000-0000-0000-0000-000000000002",
                "hrmanager_techcorp",
                "hrmanager@techcorp.com",
                passwordEncoder.encode("hrmanager123"),
                Set.of("HR_MANAGER"),
                UserStatus.ACTIVE,
                "TechCorp Solutions");

        // 3) Create HR user (password: hr123)
        createUserIfNotExists(
                "00000000-0000-0000-0000-000000000003",
                "hr_techcorp",
                "hr@techcorp.com",
                passwordEncoder.encode("hr123"),
                Set.of("HR"),
                UserStatus.ACTIVE,
                "TechCorp Solutions");

        log.info("[BOOTSTRAP] Admin/HR user seed complete");
    }

    private void createUserIfNotExists(String id, String username, String email,
            String password, Set<String> roles,
            UserStatus status, String company) {
        // Check by email (primary identifier for cross-service sync)
        if (userRepository.findByEmail(email).isEmpty()) {
            User user = User.builder()
                    .id(id)
                    .username(username)
                    .email(email)
                    .password(password)
                    .roles(roles)
                    .status(status)
                    .company(company)
                    .build();

            userRepository.save(user);
            log.info("[BOOTSTRAP] Created user: {} with role: {}", username, roles);
        } else {
            log.debug("[BOOTSTRAP] User {} already exists, skipping", email);
        }
    }
}
