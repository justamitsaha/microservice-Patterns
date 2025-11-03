package com.saha.amit.customerMvc.service;

import com.saha.amit.customerMvc.dto.CustomerRequest;
import com.saha.amit.customerMvc.dto.CustomerResponse;
import com.saha.amit.customerMvc.dto.LoginRequest;
import com.saha.amit.customerMvc.dto.OrderResponse;
import com.saha.amit.customerMvc.model.CustomerEntity;
import com.saha.amit.customerMvc.repository.CustomerRepository;
import com.saha.amit.customerMvc.util.CustomerServiceUtil;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository repository;
    private final RestTemplate restTemplate;
    private final CustomerServiceUtil util;

    private static final int SALT_BYTES = 16;

    public List<CustomerEntity> findAll() {
        return repository.findAll();
    }

    public Optional<CustomerEntity> findById(String id) {
        return repository.findById(id);
    }

    @Transactional
    public CustomerEntity create(CustomerRequest req) {
        CustomerEntity e = new CustomerEntity();
        e.setName(req.getName());
        e.setEmail(req.getEmail());
        String salt = generateSalt();
        String hash = CustomerServiceUtil.hashPassword(req.getPassword(), salt);
        e.setPasswordSalt(salt);
        e.setPasswordHash(hash);
        return repository.save(e);
    }

    @Transactional
    public Optional<CustomerEntity> update(String id, CustomerRequest req) {
        return repository.findById(id).map(existing -> {
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

    @Transactional
    public void delete(String id) {
        repository.deleteById(id);
    }

    public Map<String, String> login(LoginRequest request) {
        Optional<CustomerEntity> userOpt = repository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            return Map.of("message", "User not found");
        }
        CustomerEntity user = userOpt.get();
        String recomputed = CustomerServiceUtil.hashPassword(request.getPassword(), user.getPasswordSalt());
        boolean ok = CustomerServiceUtil.constantTimeEquals(user.getPasswordHash(), recomputed);
        if (!ok) {
            return Map.of("message", "Invalid credentials");
        }
        String accessToken = util.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = util.generateRefreshToken(user.getId(), user.getEmail());
        return new HashMap<>() {{
            put("message", "Login successful");
            put("accessToken", accessToken);
            put("refreshToken", refreshToken);
        }};
    }

    // ---------- PUBLIC API (Async + Resilience4j) ----------
    @TimeLimiter(name = "orderService")
    @CircuitBreaker(name = "orderService", fallbackMethod = "fallbackAggregation")
    @Retry(name = "orderService")
    @Bulkhead(name = "orderService", type = Bulkhead.Type.THREADPOOL)
    public CompletableFuture<Optional<CustomerResponse>> getWithOrders(String id, String orderServiceBaseUrl) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<CustomerEntity> customer = repository.findById(id);
            if (customer.isEmpty()) return Optional.empty();

            // Build URL: /orders?customerId={id}
            URI uri = UriComponentsBuilder.fromUriString(orderServiceBaseUrl)
                    .path("/orders")
                    .queryParam("customerId", id)
                    .build(true)
                    .toUri();

            ResponseEntity<OrderResponse[]> resp =
                    restTemplate.getForEntity(uri, OrderResponse[].class);

            List<OrderResponse> orders = Arrays.asList(
                    Objects.requireNonNullElse(resp.getBody(), new OrderResponse[0])
            );

            CustomerEntity e = customer.get();
            return Optional.of(new CustomerResponse(
                    e.getId(), e.getName(), e.getEmail(), e.getCreatedAt(), orders
            ));
        });
    }

    // Fallback: return placeholder order when downstream unavailable
    @SuppressWarnings("unused")
    private CompletableFuture<Optional<CustomerResponse>> fallbackAggregation(
            String id, String orderServiceBaseUrl, Throwable ex) {

        return CompletableFuture.supplyAsync(() -> {
            Optional<CustomerEntity> customer = repository.findById(id);
            if (customer.isEmpty()) return Optional.empty();

            CustomerEntity e = customer.get();
            List<OrderResponse> orders = List.of(
                    new OrderResponse("N/A", id, 0.0, "SERVICE_UNAVAILABLE")
            );
            return Optional.of(new CustomerResponse(
                    e.getId(), e.getName(), e.getEmail(), e.getCreatedAt(), orders
            ));
        });
    }


    private static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
}
