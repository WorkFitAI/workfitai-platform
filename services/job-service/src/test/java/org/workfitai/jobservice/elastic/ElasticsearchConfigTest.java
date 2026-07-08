package org.workfitai.jobservice.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

class ElasticsearchConfigTest {

  private ElasticsearchConfig config;

  @BeforeEach
  void setUp() {
    config = new ElasticsearchConfig();

    ReflectionTestUtils.setField(config, "host", "localhost");
    ReflectionTestUtils.setField(config, "port", 9200);
    ReflectionTestUtils.setField(config, "scheme", "http");
    ReflectionTestUtils.setField(config, "username", "elastic");
    ReflectionTestUtils.setField(config, "password", "");
  }

  @Test
  void shouldCreateRestClientWithoutAuthentication() {
    RestClient client = config.restClient();

    assertThat(client).isNotNull();

    try {
      client.close();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  @Test
  void shouldCreateRestClientWithAuthentication() throws Exception {
    ReflectionTestUtils.setField(config, "password", "secret");

    RestClient client = config.restClient();

    assertThat(client).isNotNull();

    client.close();
  }

  @Test
  void shouldCreateObjectMapper() {
    ObjectMapper mapper = config.elasticsearchObjectMapper();

    assertThat(mapper).isNotNull();
    assertThat(mapper.getRegisteredModuleIds())
        .anyMatch(id -> id.toString().contains("jackson-datatype-jsr310"));

    assertThat(
        mapper.getSerializationConfig()
            .isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
        .isFalse();
  }

  @Test
  void shouldCreateTransport() throws Exception {
    RestClient restClient = config.restClient();
    ObjectMapper mapper = config.elasticsearchObjectMapper();

    ElasticsearchTransport transport = config.elasticsearchTransport(restClient, mapper);

    assertThat(transport).isNotNull();

    restClient.close();
  }

  @Test
  void shouldCreateElasticsearchClient() throws Exception {
    RestClient restClient = config.restClient();
    ObjectMapper mapper = config.elasticsearchObjectMapper();

    ElasticsearchTransport transport = config.elasticsearchTransport(restClient, mapper);

    ElasticsearchClient client = config.elasticsearchClient(transport);

    assertThat(client).isNotNull();

    restClient.close();
  }
}