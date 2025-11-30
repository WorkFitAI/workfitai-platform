package org.workfitai.cvservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(exclude = {MongoAutoConfiguration.class})
@EnableFeignClients(basePackages = "org.workfitai.cvservice.client")
public class CvServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CvServiceApplication.class, args);
    }

}
