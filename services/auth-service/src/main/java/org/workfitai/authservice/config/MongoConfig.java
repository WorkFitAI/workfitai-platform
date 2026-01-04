package org.workfitai.authservice.config;

import com.mongodb.client.MongoCollection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
@EnableMongoAuditing
@RequiredArgsConstructor
@Slf4j
public class MongoConfig {

    /**
     * Create TTL indexes for auto-expiration of documents
     * MongoDB will automatically delete documents when expiresAt field is reached
     */
    @Bean
    public CommandLineRunner createTtlIndexes(MongoTemplate mongoTemplate) {
        return args -> {
            try {
                // TTL index for user_sessions collection
                createTtlIndex(mongoTemplate, "user_sessions", "expiresAt");

                // TTL index for password_reset_tokens collection
                createTtlIndex(mongoTemplate, "password_reset_tokens", "expiresAt");

                log.info("MongoDB TTL indexes created successfully");
            } catch (Exception e) {
                log.warn("Failed to create TTL indexes (may already exist): {}", e.getMessage());
            }
        };
    }

    private void createTtlIndex(MongoTemplate mongoTemplate, String collectionName, String fieldName) {
        MongoCollection<Document> collection = mongoTemplate.getCollection(collectionName);

        // Create TTL index with expireAfterSeconds=0 (expire at exact time in field)
        Document indexKeys = new Document(fieldName, 1);
        Document indexOptions = new Document("expireAfterSeconds", 0)
                .append("name", fieldName + "_ttl");

        collection.createIndex(indexKeys, new com.mongodb.client.model.IndexOptions()
                .expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS)
                .name(fieldName + "_ttl"));

        log.info("Created TTL index on {}.{}", collectionName, fieldName);
    }
}