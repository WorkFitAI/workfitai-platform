package org.workfitai.monitoringservice.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.vault.support.VaultResponse;
import org.workfitai.monitoringservice.service.ConfigurationService;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class VaultInitializerTest {

    @Mock
    private ConfigurationService configurationService;

    private VaultInitializer vaultInitializer;

    private void init() {
        vaultInitializer = new VaultInitializer(configurationService);
    }

    @Test
    void run_logsSuccess_whenConfigLoaded() throws Exception {
        init();
        VaultResponse response = new VaultResponse();
        response.setData(Map.of("key", "value"));
        when(configurationService.getServiceConfig(eq("monitoring-service"))).thenReturn(response);

        vaultInitializer.run();
        // no exception thrown means validation completed on the success path
    }

    @Test
    void run_logsWarning_whenConfigIsNull() throws Exception {
        init();
        when(configurationService.getServiceConfig(eq("monitoring-service"))).thenReturn(null);

        vaultInitializer.run();
        // no exception thrown means validation completed on the missing-config path
    }

    @Test
    void run_logsWarning_whenConfigDataEmpty() throws Exception {
        init();
        VaultResponse response = new VaultResponse();
        response.setData(Map.of());
        when(configurationService.getServiceConfig(eq("monitoring-service"))).thenReturn(response);

        vaultInitializer.run();
        // no exception thrown means validation completed on the empty-data path
    }

    @Test
    void run_swallowsException_whenConfigurationServiceThrows() throws Exception {
        init();
        when(configurationService.getServiceConfig(eq("monitoring-service")))
                .thenThrow(new RuntimeException("vault unreachable"));

        vaultInitializer.run();
        // exception is caught and logged, not propagated
    }
}
