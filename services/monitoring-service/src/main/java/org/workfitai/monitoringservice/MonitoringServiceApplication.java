package org.workfitai.monitoringservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class MonitoringServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonitoringServiceApplication.class, args);
    }
}
