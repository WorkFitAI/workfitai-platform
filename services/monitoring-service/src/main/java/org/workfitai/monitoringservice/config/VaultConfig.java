package org.workfitai.monitoringservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.web.client.RestTemplate;

@Configuration
public class VaultConfig {

    @Value("${vault.host:vault}")
    private String vaultHost;

    @Value("${vault.port:8200}")
    private int vaultPort;

    @Value("${vault.token:dev-token}")
    private String vaultToken;

    @Bean
    public VaultTemplate vaultTemplate() {
        VaultEndpoint endpoint = VaultEndpoint.create(vaultHost, vaultPort);
        endpoint.setScheme("http");

        return new VaultTemplate(endpoint, new TokenAuthentication(vaultToken));
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}