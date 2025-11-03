package com.saha.amit.customerMvc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomerServiceMvcApplication {
    public static final Logger logger = LoggerFactory.getLogger(CustomerServiceMvcApplication.class);
    public static void main(String[] args) {
        logger.info("http://localhost:8082/swagger-ui/swagger-ui/index.html");
        SpringApplication.run(CustomerServiceMvcApplication.class, args);
    }
}

