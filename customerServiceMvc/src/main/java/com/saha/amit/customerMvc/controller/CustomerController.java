package com.saha.amit.customerMvc.controller;

import com.saha.amit.customerMvc.dto.CustomerRequest;
import com.saha.amit.customerMvc.dto.CustomerResponse;
import com.saha.amit.customerMvc.dto.LoginRequest;
import com.saha.amit.customerMvc.model.CustomerEntity;
import com.saha.amit.customerMvc.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService service;

    @Value("${app.order-service.base-url:http://localhost:8080}")
    private String orderServiceBaseUrl;

    @GetMapping
    public ResponseEntity<List<CustomerEntity>> list() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> get(@PathVariable String id) {
        Optional<CustomerResponse> resp = service.getWithOrders(id, orderServiceBaseUrl).join();
        return resp.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }


    @PostMapping
    public ResponseEntity<CustomerEntity> register(@RequestBody CustomerRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerEntity> update(@PathVariable String id, @RequestBody CustomerRequest request) {
        return service.update(id, request).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
        Map<String, String> res = service.login(request);
        if ("Login successful".equals(res.get("message"))) {
            return ResponseEntity.ok(res);
        }
        return ResponseEntity.status(401).body(res);
    }
}

