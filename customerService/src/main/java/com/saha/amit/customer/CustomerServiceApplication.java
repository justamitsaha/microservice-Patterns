package com.saha.amit.customer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomerServiceApplication {
    public static final Logger logger = LoggerFactory.getLogger(CustomerServiceApplication.class);
    public static void main(String[] args) {
        logger.info("http://localhost:8081/actuator/health");
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}

