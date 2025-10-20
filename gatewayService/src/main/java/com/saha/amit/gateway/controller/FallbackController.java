package com.saha.amit.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class FallbackController {

    @GetMapping("/fallback/orders")
    public ResponseEntity<Map<String, Object>> ordersFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "service", "order-service",
                        "message", "Order service is temporarily unavailable",
                        "status", 503
                ));
    }

    @GetMapping("/fallback/customers")
    public ResponseEntity<Map<String, Object>> customersFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "service", "customer-service",
                        "message", "Customer service is temporarily unavailable",
                        "status", 503
                ));
    }
}

