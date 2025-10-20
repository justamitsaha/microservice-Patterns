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
        return repository.save(e);
    }

    public Mono<CustomerEntity> update(String id, CustomerRequest req) {
        return repository.findById(id)
                .flatMap(existing -> {
                    existing.setName(req.getName());
                    existing.setEmail(req.getEmail());
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
}
