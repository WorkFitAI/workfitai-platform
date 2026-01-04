package org.workfitai.cvservice.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Configuration
public class MongoConfig {

    @Value("${mongodb.host:cv-mongo}")
    private String host;

    @Value("${mongodb.port:27017}")
    private int port;

    @Value("${mongodb.database:cv-db}")
    private String database;

    @Value("${mongodb.username:user}")
    private String username;

    @Value("${mongodb.password:123456}")
    private String password;

    @Value("${mongodb.auth-database:cv-db}")
    private String authDatabase;

    @Bean
    @Primary
    @ConditionalOnMissingBean(MongoClient.class)
    public MongoClient mongoClient() {
        MongoCredential credential = MongoCredential.createCredential(
                username, authDatabase, password.toCharArray());

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyToClusterSettings(builder -> builder.hosts(List.of(new ServerAddress(host, port))))
                .credential(credential)
                .build();

        return MongoClients.create(settings);
    }

    @Bean
    @Primary
    public MongoTemplate mongoTemplate(MongoClient mongoClient) {
        return new MongoTemplate(mongoClient, database);
    }
}