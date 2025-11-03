package com.saha.amit.customerMvc.integration;

import com.saha.amit.customerMvc.model.CustomerEntity;
import com.saha.amit.customerMvc.repository.CustomerRepository;
import com.saha.amit.customerMvc.service.CustomerService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "resilience4j.circuitbreaker.instances.orderService.slidingWindowType=COUNT_BASED",
        "resilience4j.circuitbreaker.instances.orderService.slidingWindowSize=2",
        "resilience4j.circuitbreaker.instances.orderService.minimumNumberOfCalls=2",
        "resilience4j.circuitbreaker.instances.orderService.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.orderService.waitDurationInOpenState=1s",
        "resilience4j.retry.instances.orderService.maxAttempts=1"  
})
class CircuitBreakerIT {

    @Autowired
    CustomerService service;

    @Autowired
    CircuitBreakerRegistry registry;

    @MockBean
    CustomerRepository repository;

    static MockWebServer server;

    @BeforeAll
    static void start() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterAll
    static void stop() throws IOException {
        server.shutdown();
    }

    @Test
    void circuitOpensAfterFailures() {
        CustomerEntity e = new CustomerEntity();
        e.setId("c1"); e.setName("n"); e.setEmail("e@x"); e.setCreatedAt(Instant.now().toEpochMilli());
        when(repository.findById("c1")).thenReturn(Optional.of(e));

        // Two failures to trip CB given the config above
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));

        String base = server.url("/").toString();
        service.getWithOrders("c1", base);
        service.getWithOrders("c1", base);

        CircuitBreaker cb = registry.circuitBreaker("orderService");
        assertThat(cb.getState()).isNotEqualTo(CircuitBreaker.State.CLOSED);
    }
}

