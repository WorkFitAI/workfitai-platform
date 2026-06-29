package org.workfitai.monitoringservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

    @Mock
    private VaultTemplate vaultTemplate;

    @Mock
    private RestTemplate restTemplate;

    private ConfigurationService configurationService;

    @BeforeEach
    void setUp() {
        configurationService = new ConfigurationService(vaultTemplate, restTemplate);
    }

    @Test
    void initializeServiceConfig_returnsTrue_onSuccess() {
        boolean result = configurationService.initializeServiceConfig("user-service", Map.of("key", "value"));

        assertThat(result).isTrue();
        verify(vaultTemplate).write(eq("secret/data/user-service"), any());
    }

    @Test
    void initializeServiceConfig_returnsFalse_whenVaultWriteThrows() {
        doThrow(new RuntimeException("vault unreachable")).when(vaultTemplate).write(anyString(), any());

        boolean result = configurationService.initializeServiceConfig("user-service", Map.of("key", "value"));

        assertThat(result).isFalse();
    }

    @Test
    void getServiceConfig_returnsVaultResponse_onSuccess() {
        VaultResponse response = new VaultResponse();
        when(vaultTemplate.read("secret/data/user-service")).thenReturn(response);

        VaultResponse result = configurationService.getServiceConfig("user-service");

        assertThat(result).isSameAs(response);
    }

    @Test
    void getServiceConfig_returnsNull_whenVaultThrows() {
        when(vaultTemplate.read(anyString())).thenThrow(new RuntimeException("vault unreachable"));

        VaultResponse result = configurationService.getServiceConfig("user-service");

        assertThat(result).isNull();
    }

    @Test
    void updateServiceConfig_returnsTrue_onSuccess() {
        boolean result = configurationService.updateServiceConfig("auth-service", Map.of("k", "v"));

        assertThat(result).isTrue();
        verify(vaultTemplate).write(eq("secret/data/auth-service"), any());
    }

    @Test
    void updateServiceConfig_returnsFalse_whenVaultWriteThrows() {
        doThrow(new RuntimeException("vault down")).when(vaultTemplate).write(anyString(), any());

        boolean result = configurationService.updateServiceConfig("auth-service", Map.of("k", "v"));

        assertThat(result).isFalse();
    }

    @Test
    void refreshService_returnsFalse_whenServiceUnknown() {
        boolean result = configurationService.refreshService("unknown-service");

        assertThat(result).isFalse();
    }

    @Test
    void refreshService_returnsTrue_onSuccessfulRefresh() {
        when(restTemplate.postForEntity(eq("http://user-service:9005/actuator/refresh"), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("[]"));

        boolean result = configurationService.refreshService("user-service");

        assertThat(result).isTrue();
    }

    @Test
    void refreshService_returnsFalse_whenRestTemplateThrows() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        boolean result = configurationService.refreshService("user-service");

        assertThat(result).isFalse();
    }

    @Test
    void getAllServicesStatus_returnsEntryForEachKnownService() {
        when(vaultTemplate.read(anyString())).thenReturn(null);
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new RestClientException("down"));

        Map<String, Object> status = configurationService.getAllServicesStatus();

        assertThat(status).containsKeys("user-service", "auth-service", "job-service", "cv-service", "application-service");
    }

    @Test
    void getAllServicesStatus_marksServiceHealthy_whenHealthCheckSucceeds() {
        VaultResponse response = new VaultResponse();
        when(vaultTemplate.read(anyString())).thenReturn(response);
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(ResponseEntity.ok("UP"));

        Map<String, Object> status = configurationService.getAllServicesStatus();

        @SuppressWarnings("unchecked")
        Map<String, Object> userServiceStatus = (Map<String, Object>) status.get("user-service");
        assertThat(userServiceStatus.get("configExists")).isEqualTo(true);
        assertThat(userServiceStatus.get("healthy")).isEqualTo(true);
    }
}
