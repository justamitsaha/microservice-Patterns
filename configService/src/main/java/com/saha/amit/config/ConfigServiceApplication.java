package com.saha.amit.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@EnableConfigServer
@SpringBootApplication
public class ConfigServiceApplication {
    public static final Logger logger = LoggerFactory.getLogger(ConfigServiceApplication.class);
    public static void main(String[] args) {
        logger.info("http://localhost:8888/actuator/health");
        logger.info("http://localhost:8888/order-service/dev");
        logger.info("http://localhost:8888/customer-service/default/main");
        logger.info("http://localhost:8888/gateway-service/default/main");
        logger.info("http://localhost:8888/discovery-service/default/main");
        logger.info("curl -X POST http://localhost:8080/actuator/busrefresh");
        SpringApplication.run(ConfigServiceApplication.class, args);
    }
}

