package com.saha.amit.customer.service;

import com.saha.amit.customer.dto.CustomerRequest;
import com.saha.amit.customer.dto.CustomerResponse;
import com.saha.amit.customer.dto.OrderResponse;
import com.saha.amit.customer.model.CustomerEntity;
import com.saha.amit.customer.repository.CustomerRepository;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Additional unit tests focused on branches and fallbacks in CustomerService.
 */
class CustomerServiceUnitTest {

    private CustomerRepository repository;
    private CustomerService service;
    private static MockWebServer server;

    @BeforeAll
    static void start() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterAll
    static void stop() throws IOException {
        server.shutdown();
    }

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(CustomerRepository.class);
        String base = server.url("/").toString();
        WebClient orderWebClient = WebClient.builder().baseUrl(base).build();
        service = new CustomerService(
                repository,
                orderWebClient,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                BulkheadRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults()
        );
    }

    @Test
    void updateWithoutPasswordKeepsExistingHash() {
        CustomerEntity existing = new CustomerEntity();
        existing.setId("id-2");
        existing.setName("N");
        existing.setEmail("e@x");
        existing.setPasswordSalt("S");
        existing.setPasswordHash("H");
        when(repository.findById("id-2")).thenReturn(Mono.just(existing));
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        CustomerRequest req = new CustomerRequest();
        req.setName("New");
        req.setEmail("new@x");
        // no password provided

        StepVerifier.create(service.update("id-2", req))
                .assertNext(updated -> {
                    assertThat(updated.getPasswordSalt()).isEqualTo("S");
                    assertThat(updated.getPasswordHash()).isEqualTo("H");
                    assertThat(updated.getName()).isEqualTo("New");
                    assertThat(updated.getEmail()).isEqualTo("new@x");
                })
                .verifyComplete();
    }

    @Test
    void getWithOrdersFallsBackOnDownstreamFailure() {
        CustomerEntity e = new CustomerEntity();
        e.setId("c1");
        e.setName("User");
        e.setEmail("u@e.com");
        e.setCreatedAt(Instant.now().toEpochMilli());
        when(repository.findById("c1")).thenReturn(Mono.just(e));

        // Simulate 500 from order service
        server.enqueue(new MockResponse().setResponseCode(500));

        Mono<CustomerResponse> mono = service.getWithOrders("c1");

        StepVerifier.create(mono)
                .assertNext(resp -> {
                    assertThat(resp.getId()).isEqualTo("c1");
                    List<OrderResponse> orders = resp.getOrders();
                    assertThat(orders).hasSize(1);
                    assertThat(orders.get(0).getStatus())
                            .isIn("SERVICE_UNAVAILABLE", "TIMEOUT_FALLBACK");
                })
                .verifyComplete();
    }

    @Test
    void findAllAndFindByIdPaths() {
        when(repository.findAll()).thenReturn(Flux.empty());
        when(repository.findById("nope")).thenReturn(Mono.empty());

        StepVerifier.create(service.findAll()).verifyComplete();
        StepVerifier.create(service.findById("nope")).verifyComplete();
    }
}
