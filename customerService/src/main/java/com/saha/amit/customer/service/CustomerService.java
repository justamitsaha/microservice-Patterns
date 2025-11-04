package com.saha.amit.customer.service;

import com.saha.amit.customer.dto.*;
import com.saha.amit.customer.model.CustomerEntity;
import com.saha.amit.customer.repository.CustomerRepository;
import com.saha.amit.customer.util.CustomerServiceUtil;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository repository;
    private final WebClient orderWebClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;

    public Flux<CustomerEntity> findAll() {
        return repository.findAll();
    }

    public Mono<CustomerEntity> findById(String id) {
        return repository.findById(id);
    }

    public Mono<CustomerEntity> create(CustomerRequest req) {
        CustomerEntity e = new CustomerEntity();
        e.setName(req.getName());
        e.setEmail(req.getEmail());
        e.setCreatedAt(Instant.now().toEpochMilli());
        String salt = generateSalt();
        String hash = CustomerServiceUtil.hashPassword(req.getPassword(), salt);
        e.setPasswordSalt(salt);
        e.setPasswordHash(hash);
        return repository.save(e);
    }

    public Mono<CustomerEntity> update(String id, CustomerRequest req) {
        return repository.findById(id)
                .flatMap(existing -> {
                    existing.setName(req.getName());
                    existing.setEmail(req.getEmail());
                    if (req.getPassword() != null && !req.getPassword().isBlank()) {
                        String salt = generateSalt();
                        String hash = CustomerServiceUtil.hashPassword(req.getPassword(), salt);
                        existing.setPasswordSalt(salt);
                        existing.setPasswordHash(hash);
                    }
                    return repository.save(existing);
                });
    }

    public Mono<Void> delete(String id) {
        return repository.deleteById(id);
    }

    public Mono<CustomerResponse> getWithOrders(String id) {

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("orderService");
        Retry retry = retryRegistry.retry("orderService");
        Bulkhead bh = bulkheadRegistry.bulkhead("orderService");

        Duration timeout = Duration.ofSeconds(2);

        Mono<CustomerEntity> customerMono = repository.findById(id);

        Flux<OrderResponse> ordersFlux = orderWebClient.get()
                .uri(uri -> uri.path("/orders").queryParam("customerId", id).build())
                .retrieve()
                .bodyToFlux(OrderResponse.class)
                .timeout(timeout)
                .transformDeferred(RetryOperator.of(retry))            // ✅ Retry first
                .transformDeferred(CircuitBreakerOperator.of(cb))     // ✅ Then CB
                .transformDeferred(BulkheadOperator.of(bh))           // ✅ Then BH
                .onErrorResume(this::fallbackOrders);                 // ✅ Central fallback

        return customerMono
                .zipWith(ordersFlux.collectList(), this::toResponse);
    }

    private Flux<OrderResponse> fallbackOrders(Throwable ex) {
        if (ex instanceof TimeoutException) {
            return Flux.just(new OrderResponse("N/A", null, 0.0, "TIMEOUT_FALLBACK"));
        }
        return Flux.just(new OrderResponse("N/A", null, 0.0, "SERVICE_UNAVAILABLE"));
    }


    public CustomerResponse toResponse(CustomerEntity e, List<OrderResponse> orders) {
        return new CustomerResponse(e.getId(), e.getName(), e.getEmail(), e.getCreatedAt(), orders);
    }

    // Password helpers
    private static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return java.util.Base64.getEncoder().encodeToString(salt);
    }


    public Mono<LoginResponse> login(LoginRequest request) {
        return repository.findByEmail(request.getEmail())
                .map(user -> {
                    String recomputed = CustomerServiceUtil.hashPassword(request.getPassword(), user.getPasswordSalt());
                    boolean ok = CustomerServiceUtil.constantTimeEquals(user.getPasswordHash(), recomputed);
                    return new LoginResponse(ok, ok ? "Login successful" : "Invalid credentials");
                })
                .defaultIfEmpty(new LoginResponse(false, "User not found"));
    }


}
