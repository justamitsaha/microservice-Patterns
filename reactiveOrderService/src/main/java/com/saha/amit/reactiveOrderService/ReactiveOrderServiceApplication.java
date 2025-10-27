package com.saha.amit.reactiveOrderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReactiveOrderServiceApplication {

    private final static Logger logger = LoggerFactory.getLogger(ReactiveOrderServiceApplication.class);
    public static void main(String[] args) {
        String swagger_UI = "http://localhost:8080/swagger-ui/index.html";
        logger.info("http://localhost:8080/actuator/health");
        logger.info("Swagger UI, {} ", swagger_UI);
        SpringApplication.run(ReactiveOrderServiceApplication.class, args);
    }

}

