package com.saha.amit.customer.controller;

import com.saha.amit.customer.dto.CustomerRequest;
import com.saha.amit.customer.dto.CustomerResponse;
import com.saha.amit.customer.dto.LoginRequest;
import com.saha.amit.customer.dto.LoginResponse;
import com.saha.amit.customer.model.CustomerEntity;
import com.saha.amit.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
@Slf4j
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService service;

    @GetMapping
    public ResponseEntity<Flux<CustomerEntity>> list() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<CustomerResponse>> get(@PathVariable String id) {
        log.info("Fetching customer with ID: {}", id);
        return service.getWithOrders(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Mono<CustomerEntity>> register(@RequestBody CustomerRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<CustomerEntity>> update(@PathVariable String id, @RequestBody CustomerRequest request) {
        return service.update(id, request)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String id) {
        return service.findById(id)
                .flatMap(existing -> service.delete(id).thenReturn(ResponseEntity.noContent().<Void>build()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@RequestBody LoginRequest request) {
        return service.login(request).map(ResponseEntity::ok);
    }
}
