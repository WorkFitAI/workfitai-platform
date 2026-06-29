package org.workfitai.monitoringservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.vault.support.VaultResponse;
import org.workfitai.monitoringservice.config.VaultInitializer;
import org.workfitai.monitoringservice.service.ConfigurationService;

import java.util.Map;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VaultController.class)
@AutoConfigureMockMvc(addFilters = false)
class VaultControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ConfigurationService configurationService;
    @MockBean
    private VaultInitializer vaultInitializer;

    @Test
    void reinitializeSecrets_returnsOk_onSuccess() throws Exception {
        doNothing().when(vaultInitializer).run();

        mockMvc.perform(post("/api/vault/reinitialize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void reinitializeSecrets_returns500_onException() throws Exception {
        doThrow(new RuntimeException("vault unreachable")).when(vaultInitializer).run();

        mockMvc.perform(post("/api/vault/reinitialize"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void getVaultStatus_returnsConfiguredAndMissingPerService() throws Exception {
        VaultResponse configured = new VaultResponse();
        configured.setData(Map.of("key", "value"));
        when(configurationService.getServiceConfig("auth-service")).thenReturn(configured);
        when(configurationService.getServiceConfig("user-service")).thenReturn(null);
        when(configurationService.getServiceConfig("api-gateway")).thenReturn(null);
        when(configurationService.getServiceConfig("job-service")).thenReturn(null);
        when(configurationService.getServiceConfig("cv-service")).thenReturn(null);
        when(configurationService.getServiceConfig("application-service")).thenReturn(null);
        when(configurationService.getServiceConfig("monitoring-service")).thenReturn(null);

        mockMvc.perform(get("/api/vault/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services['auth-service']").value("configured"))
                .andExpect(jsonPath("$.services['user-service']").value("missing"));
    }

    @Test
    void getVaultStatus_reportsErrorPerService_onException() throws Exception {
        when(configurationService.getServiceConfig("auth-service"))
                .thenThrow(new RuntimeException("boom"));
        when(configurationService.getServiceConfig("user-service")).thenReturn(null);
        when(configurationService.getServiceConfig("api-gateway")).thenReturn(null);
        when(configurationService.getServiceConfig("job-service")).thenReturn(null);
        when(configurationService.getServiceConfig("cv-service")).thenReturn(null);
        when(configurationService.getServiceConfig("application-service")).thenReturn(null);
        when(configurationService.getServiceConfig("monitoring-service")).thenReturn(null);

        mockMvc.perform(get("/api/vault/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services['auth-service']").value("error: boom"));
    }

    @Test
    void getServiceConfig_returnsFound_whenConfigPresent() throws Exception {
        VaultResponse vaultResponse = new VaultResponse();
        vaultResponse.setData(Map.of("key", "value"));
        when(configurationService.getServiceConfig("auth-service")).thenReturn(vaultResponse);

        mockMvc.perform(get("/api/vault/config/auth-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("found"));
    }

    @Test
    void getServiceConfig_returnsNotFound_whenConfigAbsent() throws Exception {
        when(configurationService.getServiceConfig("auth-service")).thenReturn(null);

        mockMvc.perform(get("/api/vault/config/auth-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("not_found"));
    }

    @Test
    void getServiceConfig_returns500_onException() throws Exception {
        when(configurationService.getServiceConfig("auth-service"))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/api/vault/config/auth-service"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"));
    }
}
