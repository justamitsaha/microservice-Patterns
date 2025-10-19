package com.saha.amit.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@EnableEurekaServer
@SpringBootApplication
public class DiscoveryServiceApplication {
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryServiceApplication.class);
    public static void main(String[] args) {
        logger.info("http://localhost:8761/");
        SpringApplication.run(DiscoveryServiceApplication.class, args);
    }
}

