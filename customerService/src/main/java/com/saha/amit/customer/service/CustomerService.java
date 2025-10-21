package com.saha.amit.customer.service;

import com.saha.amit.customer.dto.CustomerRequest;
import com.saha.amit.customer.dto.CustomerResponse;
import com.saha.amit.customer.dto.OrderResponse;
import com.saha.amit.customer.model.CustomerEntity;
import com.saha.amit.customer.repository.CustomerRepository;
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
        String hash = hashPassword(req.getPassword(), salt);
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
                        String hash = hashPassword(req.getPassword(), salt);
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
        Mono<CustomerEntity> customerMono = repository.findById(id);

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("orderService");
        Retry retry = retryRegistry.retry("orderService");
        Bulkhead bh = bulkheadRegistry.bulkhead("orderService");

        Duration timeout = Duration.ofSeconds(2);

        Flux<OrderResponse> ordersFlux = orderWebClient.get()
                .uri(uri -> uri.path("/orders").queryParam("customerId", id).build())
                .retrieve()
                .bodyToFlux(OrderResponse.class)
                .timeout(timeout) // ✅ Native Reactor timeout replaces TimeLimiterOperator
                .transformDeferred(CircuitBreakerOperator.of(cb))
                .transformDeferred(BulkheadOperator.of(bh))
                .transformDeferred(RetryOperator.of(retry))
                .onErrorResume(TimeoutException.class,
                        ex -> Flux.just(new OrderResponse("N/A", id, 0.0, "TIMEOUT")))
                .onErrorResume(ex -> Flux.just(new OrderResponse("N/A", id, 0.0, "SERVICE_UNAVAILABLE")));

        return customerMono.zipWith(ordersFlux.collectList())
                .map(tuple -> toResponse(tuple.getT1(), tuple.getT2()))
                .switchIfEmpty(Mono.empty());
    }

    public static CustomerResponse toResponse(CustomerEntity e, List<OrderResponse> orders) {
        return new CustomerResponse(e.getId(), e.getName(), e.getEmail(), e.getCreatedAt(), orders);
    }

    // Password helpers
    private static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return java.util.Base64.getEncoder().encodeToString(salt);
    }

    private static String hashPassword(String password, String base64Salt) {
        try {
            byte[] salt = java.util.Base64.getDecoder().decode(base64Salt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    public reactor.core.publisher.Mono<com.saha.amit.customer.dto.LoginResponse> login(com.saha.amit.customer.dto.LoginRequest request) {
        return repository.findByEmail(request.getEmail())
                .map(user -> {
                    String recomputed = hashPassword(request.getPassword(), user.getPasswordSalt());
                    boolean ok = constantTimeEquals(user.getPasswordHash(), recomputed);
                    return new com.saha.amit.customer.dto.LoginResponse(ok, ok ? "Login successful" : "Invalid credentials");
                })
                .defaultIfEmpty(new com.saha.amit.customer.dto.LoginResponse(false, "User not found"));
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int res = 0;
        for (int i = 0; i < a.length(); i++) {
            res |= a.charAt(i) ^ b.charAt(i);
        }
        return res == 0;
    }
}
