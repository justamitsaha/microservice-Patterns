package com.saha.amit.customer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomerServiceApplication {
    public static final Logger logger = LoggerFactory.getLogger(CustomerServiceApplication.class);
    public static void main(String[] args) {
        logger.info("Customer Service Application Started");
        logger.info("http://localhost:8082/actuator/health");
        logger.info("http://localhost:8082/swagger-ui/webjars/swagger-ui/index.html");
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}

