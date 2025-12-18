package org.workfitai.userservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.workfitai.userservice.dto.elasticsearch.ReindexJobResponse;
import org.workfitai.userservice.dto.elasticsearch.ReindexRequest;
import org.workfitai.userservice.dto.kafka.UserDocument;
import org.workfitai.userservice.dto.response.UserBaseResponse;
import org.workfitai.userservice.model.UserEntity;
import org.workfitai.userservice.repository.UserRepository;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing Elasticsearch index initialization and bulk reindexing
 * operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserIndexManagementService {

    private final ElasticsearchClient elasticsearchClient;
    private final UserRepository userRepository;
    private static final String INDEX_NAME = "users-index";
    private static final int BATCH_SIZE = 100;

    /**
     * Initialize Elasticsearch index on application startup if it doesn't exist
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndex() {
        try {
            boolean exists = elasticsearchClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(INDEX_NAME)))
                    .value();

            if (!exists) {
                log.info("Creating Elasticsearch index: {}", INDEX_NAME);
                createIndex();
                log.info("Successfully created Elasticsearch index: {}", INDEX_NAME);
            } else {
                log.info("Elasticsearch index already exists: {}", INDEX_NAME);
            }
        } catch (Exception e) {
            log.error("Failed to initialize Elasticsearch index: {}", e.getMessage(), e);
            // Don't fail application startup if Elasticsearch is unavailable
        }
    }

    private void createIndex() throws Exception {
        // Read mapping from resources
        InputStream mappingStream = getClass().getResourceAsStream("/elasticsearch/users-index-mapping.json");

        if (mappingStream == null) {
            log.warn("Index mapping file not found, creating index with default settings");
            elasticsearchClient.indices().create(c -> c.index(INDEX_NAME));
        } else {
            elasticsearchClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(INDEX_NAME)
                    .withJson(mappingStream)));
        }
    }

    /**
     * Trigger bulk reindexing of all users from PostgreSQL to Elasticsearch
     */
    @Async
    public CompletableFuture<ReindexJobResponse> triggerReindex(ReindexRequest request) {
        String jobId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        log.info("Starting reindex job: jobId={}, batchSize={}", jobId, request.getBatchSize());

        try {
            long totalUsers = userRepository.count();
            long processedUsers = 0;
            int batchSize = request.getBatchSize() != null ? request.getBatchSize() : BATCH_SIZE;
            int pageNumber = 0;

            while (processedUsers < totalUsers) {
                // Use findAllWithHrProfile to eagerly fetch HR profile data
                Page<UserEntity> userPage = userRepository.findAllWithHrProfile(PageRequest.of(pageNumber, batchSize));

                for (UserEntity user : userPage.getContent()) {
                    try {
                        indexUser(user);
                        processedUsers++;
                    } catch (Exception e) {
                        log.error("Failed to index user: userId={}, error={}", user.getUserId(), e.getMessage(), e);
                        // Continue with next user
                    }
                }

                pageNumber++;

                if (!userPage.hasNext()) {
                    break;
                }
            }

            Instant endTime = Instant.now();
            long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();

            ReindexJobResponse response = ReindexJobResponse.builder()
                    .jobId(jobId)
                    .status("COMPLETED")
                    .totalUsers(totalUsers)
                    .processedUsers(processedUsers)
                    .failedUsers(totalUsers - processedUsers)
                    .startTime(startTime)
                    .endTime(endTime)
                    .durationMs(durationMs)
                    .build();

            log.info("Reindex job completed: jobId={}, processed={}/{}, duration={}ms",
                    jobId, processedUsers, totalUsers, durationMs);

            return CompletableFuture.completedFuture(response);

        } catch (Exception e) {
            log.error("Reindex job failed: jobId={}, error={}", jobId, e.getMessage(), e);

            ReindexJobResponse response = ReindexJobResponse.builder()
                    .jobId(jobId)
                    .status("FAILED")
                    .errorMessage(e.getMessage())
                    .startTime(startTime)
                    .endTime(Instant.now())
                    .build();

            return CompletableFuture.completedFuture(response);
        }
    }

    private void indexUser(UserEntity user) throws Exception {
        // Get company info from HrProfile if user is HR/HR_MANAGER
        String companyNo = null;
        String companyName = null;

        if (user.getHrProfile() != null) {
            companyNo = user.getHrProfile().getCompanyNo();
            companyName = user.getHrProfile().getCompanyName();
        }

        UserDocument document = UserDocument.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getUserRole().name())
                .status(user.getUserStatus().name())
                .blocked(user.isBlocked())
                .deleted(user.isDeleted())
                .companyNo(companyNo)
                .companyName(companyName)
                .createdDate(user.getCreatedDate())
                .lastModifiedDate(user.getLastModifiedDate())
                .version(user.getVersion())
                .build();

        elasticsearchClient.index(i -> i
                .index(INDEX_NAME)
                .id(user.getUserId().toString())
                .document(document)
                .refresh(Refresh.False));
    }

    /**
     * Delete and recreate index (use with caution)
     */
    public void recreateIndex() throws Exception {
        log.warn("Recreating Elasticsearch index: {}", INDEX_NAME);

        boolean exists = elasticsearchClient.indices()
                .exists(ExistsRequest.of(e -> e.index(INDEX_NAME)))
                .value();

        if (exists) {
            elasticsearchClient.indices().delete(d -> d.index(INDEX_NAME));
            log.info("Deleted existing index: {}", INDEX_NAME);
        }

        createIndex();
        log.info("Recreated index: {}", INDEX_NAME);
    }
}
