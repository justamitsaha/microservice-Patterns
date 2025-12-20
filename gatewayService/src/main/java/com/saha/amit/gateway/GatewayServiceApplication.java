package com.saha.amit.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayServiceApplication {
    private final static Logger logger = LoggerFactory.getLogger(GatewayServiceApplication.class);
    public static void main(String[] args) {
        logger.info("http://localhost:8085/actuator/health");
        logger.info("For local testing SPRING_PROFILES_ACTIVE=confluentLocal loaded from environment variable which has confluent for kafka and OTEL disabled");
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}

