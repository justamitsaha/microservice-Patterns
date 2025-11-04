package com.saha.amit.customerMvc.integration;

import com.saha.amit.customerMvc.model.CustomerEntity;
import com.saha.amit.customerMvc.repository.CustomerRepository;
import com.saha.amit.customerMvc.service.CustomerService;
import io.micrometer.core.instrument.MeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CustomerServiceResilienceIT {

    @Autowired
    CustomerService service;

    @Autowired
    MeterRegistry registry;

    @MockBean
    CustomerRepository repository;

    static MockWebServer mockServer;

    @BeforeAll
    static void start() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
    }

    @AfterAll
    static void stop() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void fallbackEngagedWhenOrderServiceFails() {
        CustomerEntity e = new CustomerEntity();
        e.setId("c1"); e.setName("User"); e.setEmail("u@e.com"); e.setCreatedAt(Instant.now().toEpochMilli());
        when(repository.findById("c1")).thenReturn(Optional.of(e));

        // Mock order service to return 500 so CircuitBreaker/Retry trigger, then fallback
        mockServer.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));

        String baseUrl = mockServer.url("/").toString();
        Object result = service.getWithOrders("c1", baseUrl);
        @SuppressWarnings("unchecked")
        Optional<com.saha.amit.customerMvc.dto.CustomerResponse> resp = (result instanceof java.util.concurrent.CompletableFuture)
                ? ((java.util.concurrent.CompletableFuture<Optional<com.saha.amit.customerMvc.dto.CustomerResponse>>) result).join()
                : (Optional<com.saha.amit.customerMvc.dto.CustomerResponse>) result;

        assertThat(resp).isPresent();
        var r = resp.orElseThrow();
        assertThat(r.getOrders()).hasSize(1);
        assertThat(r.getOrders().get(0).getStatus()).isEqualTo("SERVICE_UNAVAILABLE");

        // Verify Resilience4j metrics are published
        Assertions.assertThatCode(() -> registry.get("resilience4j.circuitbreaker.calls").meter())
                .doesNotThrowAnyException();
    }
}
