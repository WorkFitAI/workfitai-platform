package org.workfitai.userservice.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch configuration for user search and indexing.
 * Used for fast full-text search across user profiles.
 */
@Configuration
@Slf4j
public class ElasticsearchConfig {

    @Value("${elasticsearch.host:elasticsearch}")
    private String host;

    @Value("${elasticsearch.port:9200}")
    private int port;

    @Value("${elasticsearch.scheme:http}")
    private String scheme;

    @Value("${elasticsearch.username:elastic}")
    private String username;

    @Value("${elasticsearch.password:}")
    private String password;

    @Bean
    public RestClient restClient() {
        log.info("Connecting to Elasticsearch at {}://{}:{}", scheme, host, port);

        var builder = RestClient.builder(new HttpHost(host, port, scheme));

        // Add authentication if password is provided
        if (password != null && !password.isBlank()) {
            log.info("Using authenticated connection to Elasticsearch with user: {}", username);
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password));

            builder.setHttpClientConfigCallback(
                    httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }

        return builder.build();
    }

    @Bean
    public ObjectMapper elasticsearchObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Register Java 8 time module for Instant serialization
        mapper.registerModule(new JavaTimeModule());
        // Serialize dates as ISO-8601 strings instead of timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient,
            ObjectMapper elasticsearchObjectMapper) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper(elasticsearchObjectMapper));
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
