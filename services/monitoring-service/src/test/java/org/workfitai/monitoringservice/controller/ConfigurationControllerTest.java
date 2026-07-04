package org.workfitai.monitoringservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.vault.support.VaultResponse;
import org.workfitai.monitoringservice.service.ConfigurationService;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfigurationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ConfigurationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private ConfigurationService configurationService;

    @Test
    void initializeConfigs_returnsOk_onSuccess() throws Exception {
        when(configurationService.initializeServiceConfig(eq("auth-service"), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/config/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "serviceName", "auth-service",
                                "config", Map.of("key", "value")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void initializeConfigs_returnsOkWithFailureMessage_whenServiceReturnsFalse() throws Exception {
        when(configurationService.initializeServiceConfig(eq("auth-service"), any())).thenReturn(false);

        mockMvc.perform(post("/api/v1/config/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "serviceName", "auth-service",
                                "config", Map.of("key", "value")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Failed to initialize configuration"));
    }

    @Test
    void initializeConfigs_returnsBadRequest_whenMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/config/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("serviceName", "auth-service"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void initializeConfigs_returns500_onServiceException() throws Exception {
        when(configurationService.initializeServiceConfig(eq("auth-service"), any()))
                .thenThrow(new RuntimeException("vault down"));

        mockMvc.perform(post("/api/v1/config/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "serviceName", "auth-service",
                                "config", Map.of("key", "value")))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getServiceConfig_returnsOk_whenFound() throws Exception {
        VaultResponse vaultResponse = new VaultResponse();
        vaultResponse.setData(Map.of("key", "value"));
        when(configurationService.getServiceConfig("auth-service")).thenReturn(vaultResponse);

        mockMvc.perform(get("/api/v1/config/service/auth-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getServiceConfig_returnsNotFound_whenAbsent() throws Exception {
        when(configurationService.getServiceConfig("auth-service")).thenReturn(null);

        mockMvc.perform(get("/api/v1/config/service/auth-service"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getServiceConfig_returns500_onException() throws Exception {
        when(configurationService.getServiceConfig("auth-service"))
                .thenThrow(new RuntimeException("vault unreachable"));

        mockMvc.perform(get("/api/v1/config/service/auth-service"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void updateServiceConfig_returnsOk_onSuccess() throws Exception {
        when(configurationService.updateServiceConfig(eq("auth-service"), any())).thenReturn(true);

        mockMvc.perform(put("/api/v1/config/service/auth-service")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("key", "value"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateServiceConfig_returnsOkWithFailureMessage_whenServiceReturnsFalse() throws Exception {
        when(configurationService.updateServiceConfig(eq("auth-service"), any())).thenReturn(false);

        mockMvc.perform(put("/api/v1/config/service/auth-service")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("key", "value"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Failed to update configuration"));
    }

    @Test
    void refreshService_returnsOk_onSuccess() throws Exception {
        when(configurationService.refreshService("auth-service")).thenReturn(true);

        mockMvc.perform(post("/api/v1/config/refresh/auth-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void refreshService_returnsOkWithFailureMessage_whenServiceReturnsFalse() throws Exception {
        when(configurationService.refreshService("auth-service")).thenReturn(false);

        mockMvc.perform(post("/api/v1/config/refresh/auth-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Failed to refresh service"));
    }

    @Test
    void refreshService_returns500_onException() throws Exception {
        when(configurationService.refreshService("auth-service"))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/v1/config/refresh/auth-service"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getAllServicesStatus_returnsOk() throws Exception {
        when(configurationService.getAllServicesStatus()).thenReturn(Map.of("auth-service", "configured"));

        mockMvc.perform(get("/api/v1/config/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.services['auth-service']").value("configured"));
    }

    @Test
    void getAllServicesStatus_returns500_onException() throws Exception {
        when(configurationService.getAllServicesStatus()).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/api/v1/config/status"))
                .andExpect(status().isInternalServerError());
    }
}
