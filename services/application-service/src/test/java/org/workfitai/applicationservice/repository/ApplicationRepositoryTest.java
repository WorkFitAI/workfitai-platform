package org.workfitai.applicationservice.repository;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Repository integration tests using Testcontainers.
 * 
 * Tests actual MongoDB operations with a real database instance.
 * Verifies:
 * - Custom query methods
 * - Index behavior
 * - Pagination
 */
@DataMongoTest
@Testcontainers
@DisplayName("ApplicationRepository Integration Tests")
class ApplicationRepositoryTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private ApplicationRepository applicationRepository;

    // Test data
    private static final String USER_1 = "user-1";
    private static final String USER_2 = "user-2";
    private static final String JOB_1 = UUID.randomUUID().toString();
    private static final String JOB_2 = UUID.randomUUID().toString();
    private static final String CV_1 = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 EXISTS BY USER AND JOB TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("existsByUserIdAndJobId()")
    class ExistsByUserIdAndJobIdTests {

        @Test
        @DisplayName("Should return true when application exists")
        void shouldReturnTrueWhenExists() {
            // Given
            Application app = createApplication(USER_1, JOB_1, CV_1, ApplicationStatus.APPLIED);
            applicationRepository.save(app);

            // When
            boolean exists = applicationRepository.existsByUserIdAndJobId(USER_1, JOB_1);

            // Then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("Should return false when application doesn't exist")
        void shouldReturnFalseWhenNotExists() {
            // When
            boolean exists = applicationRepository.existsByUserIdAndJobId(USER_1, JOB_1);

            // Then
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("Should distinguish between different user/job combinations")
        void shouldDistinguishCombinations() {
            // Given: User1 applied to Job1
            applicationRepository.save(createApplication(USER_1, JOB_1, CV_1, ApplicationStatus.APPLIED));

            // Then: Same user, different job = not exists
            assertThat(applicationRepository.existsByUserIdAndJobId(USER_1, JOB_2)).isFalse();

            // Different user, same job = not exists
            assertThat(applicationRepository.existsByUserIdAndJobId(USER_2, JOB_1)).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 FIND BY USER TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByUserId()")
    class FindByUserIdTests {

        @Test
        @DisplayName("Should return paginated applications for user")
        void shouldReturnPaginatedApplications() {
            // Given: 3 applications for user1, 1 for user2
            applicationRepository.save(createApplication(USER_1, JOB_1, CV_1, ApplicationStatus.APPLIED));
            applicationRepository.save(createApplication(USER_1, JOB_2, CV_1, ApplicationStatus.REVIEWING));
            applicationRepository
                    .save(createApplication(USER_1, UUID.randomUUID().toString(), CV_1, ApplicationStatus.INTERVIEW));
            applicationRepository.save(createApplication(USER_2, JOB_1, CV_1, ApplicationStatus.APPLIED));

            // When
            Page<Application> page = applicationRepository.findByUserId(
                    USER_1,
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

            // Then
            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getContent()).allMatch(app -> app.getUserId().equals(USER_1));
        }

        @Test
        @DisplayName("Should return empty page when no applications")
        void shouldReturnEmptyPageWhenNoApplications() {
            // When
            Page<Application> page = applicationRepository.findByUserId(USER_1, PageRequest.of(0, 10));

            // Then
            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 FIND BY USER AND STATUS TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByUserIdAndStatus()")
    class FindByUserIdAndStatusTests {

        @Test
        @DisplayName("Should filter by status")
        void shouldFilterByStatus() {
            // Given
            applicationRepository.save(createApplication(USER_1, JOB_1, CV_1, ApplicationStatus.APPLIED));
            applicationRepository.save(createApplication(USER_1, JOB_2, CV_1, ApplicationStatus.INTERVIEW));

            // When
            Page<Application> applied = applicationRepository.findByUserIdAndStatus(
                    USER_1, ApplicationStatus.APPLIED, PageRequest.of(0, 10));
            Page<Application> interview = applicationRepository.findByUserIdAndStatus(
                    USER_1, ApplicationStatus.INTERVIEW, PageRequest.of(0, 10));

            // Then
            assertThat(applied.getTotalElements()).isEqualTo(1);
            assertThat(applied.getContent().get(0).getStatus()).isEqualTo(ApplicationStatus.APPLIED);

            assertThat(interview.getTotalElements()).isEqualTo(1);
            assertThat(interview.getContent().get(0).getStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 FIND BY JOB TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByJobId()")
    class FindByJobIdTests {

        @Test
        @DisplayName("Should return all applicants for a job")
        void shouldReturnAllApplicants() {
            // Given: 2 applicants for job1
            applicationRepository.save(createApplication(USER_1, JOB_1, CV_1, ApplicationStatus.APPLIED));
            applicationRepository.save(createApplication(USER_2, JOB_1, CV_1, ApplicationStatus.REVIEWING));
            applicationRepository.save(createApplication(USER_1, JOB_2, CV_1, ApplicationStatus.APPLIED)); // different
                                                                                                           // job

            // When
            Page<Application> page = applicationRepository.findByJobId(JOB_1, PageRequest.of(0, 10));

            // Then
            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).allMatch(app -> app.getJobId().equals(JOB_1));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 COUNT TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Count Methods")
    class CountTests {

        @Test
        @DisplayName("Should count by user correctly")
        void shouldCountByUser() {
            // Given
            applicationRepository.save(createApplication(USER_1, JOB_1, CV_1, ApplicationStatus.APPLIED));
            applicationRepository.save(createApplication(USER_1, JOB_2, CV_1, ApplicationStatus.APPLIED));
            applicationRepository.save(createApplication(USER_2, JOB_1, CV_1, ApplicationStatus.APPLIED));

            // When/Then
            assertThat(applicationRepository.countByUserId(USER_1)).isEqualTo(2);
            assertThat(applicationRepository.countByUserId(USER_2)).isEqualTo(1);
        }

        @Test
        @DisplayName("Should count by job correctly")
        void shouldCountByJob() {
            // Given
            applicationRepository.save(createApplication(USER_1, JOB_1, CV_1, ApplicationStatus.APPLIED));
            applicationRepository.save(createApplication(USER_2, JOB_1, CV_1, ApplicationStatus.APPLIED));

            // When/Then
            assertThat(applicationRepository.countByJobId(JOB_1)).isEqualTo(2);
            assertThat(applicationRepository.countByJobId(JOB_2)).isZero();
        }

        @Test
        @DisplayName("Should count by status correctly")
        void shouldCountByStatus() {
            // Given
            applicationRepository.save(createApplication(USER_1, JOB_1, CV_1, ApplicationStatus.APPLIED));
            applicationRepository.save(createApplication(USER_1, JOB_2, CV_1, ApplicationStatus.APPLIED));
            applicationRepository.save(createApplication(USER_2, JOB_1, CV_1, ApplicationStatus.INTERVIEW));

            // When/Then
            assertThat(applicationRepository.countByStatus(ApplicationStatus.APPLIED)).isEqualTo(2);
            assertThat(applicationRepository.countByStatus(ApplicationStatus.INTERVIEW)).isEqualTo(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 FIND BY USER AND JOB TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByUserIdAndJobId()")
    class FindByUserIdAndJobIdTests {

        @Test
        @DisplayName("Should return application when exists")
        void shouldReturnApplicationWhenExists() {
            // Given
            Application app = createApplication(USER_1, JOB_1, CV_1, ApplicationStatus.APPLIED);
            applicationRepository.save(app);

            // When
            Optional<Application> result = applicationRepository.findByUserIdAndJobId(USER_1, JOB_1);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo(USER_1);
            assertThat(result.get().getJobId()).isEqualTo(JOB_1);
        }

        @Test
        @DisplayName("Should return empty when not exists")
        void shouldReturnEmptyWhenNotExists() {
            // When
            Optional<Application> result = applicationRepository.findByUserIdAndJobId(USER_1, JOB_1);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 FIND BY CV TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByCvId()")
    class FindByCvIdTests {

        @Test
        @DisplayName("Should return all applications using a CV")
        void shouldReturnApplicationsUsingCv() {
            // Given
            String cv2 = UUID.randomUUID().toString();
            applicationRepository.save(createApplication(USER_1, JOB_1, CV_1, ApplicationStatus.APPLIED));
            applicationRepository.save(createApplication(USER_1, JOB_2, CV_1, ApplicationStatus.APPLIED));
            applicationRepository
                    .save(createApplication(USER_1, UUID.randomUUID().toString(), cv2, ApplicationStatus.APPLIED));

            // When
            List<Application> result = applicationRepository.findByCvId(CV_1);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(app -> app.getCvId().equals(CV_1));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════

    private Application createApplication(String userId, String jobId, String cvId, ApplicationStatus status) {
        return Application.builder()
                .userId(userId)
                .jobId(jobId)
                .cvId(cvId)
                .status(status)
                .note("Test application")
                .createdAt(Instant.now())
                .build();
    }
}
