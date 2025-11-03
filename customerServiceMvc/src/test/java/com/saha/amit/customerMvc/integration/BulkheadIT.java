package com.saha.amit.customerMvc.integration;

import com.saha.amit.customerMvc.model.CustomerEntity;
import com.saha.amit.customerMvc.repository.CustomerRepository;
import com.saha.amit.customerMvc.service.CustomerService;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "resilience4j.bulkhead.instances.orderService.maxConcurrentCalls=1",
        "resilience4j.bulkhead.instances.orderService.maxWaitDuration=0ms",
        "resilience4j.retry.instances.orderService.maxAttempts=1"
})
class BulkheadIT {

    @Autowired
    CustomerService service;

    @Autowired
    BulkheadRegistry bulkheadRegistry;

    @MockitoBean
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
    void bulkheadLimitsConcurrentCalls() throws Exception {
        CustomerEntity e = new CustomerEntity();
        e.setId("c1"); e.setName("n"); e.setEmail("e@x"); e.setCreatedAt(Instant.now().toEpochMilli());
        when(repository.findById("c1")).thenReturn(Optional.of(e));

        // Dispatcher that delays response to hold the first call
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                Thread.sleep(1500);
                return new MockResponse().setResponseCode(200).setBody("[]");
            }
        });

        String base = server.url("/").toString();
        Bulkhead bh = bulkheadRegistry.bulkhead("orderService");

        // Start first call (will block inside server dispatcher)
        CompletableFuture.runAsync(() -> service.getWithOrders("c1", base));

        // Wait a moment until first acquires bulkhead permit
        TimeUnit.MILLISECONDS.sleep(200);
        int availableDuringFirst = bh.getMetrics().getAvailableConcurrentCalls();

        // Second call should be rejected immediately due to maxConcurrentCalls=1 and maxWaitDuration=0ms
        var second = service.getWithOrders("c1", base);

        assertThat(availableDuringFirst).isEqualTo(0);
        assertThat(second).isPresent();
        assertThat(second.get().getOrders()).hasSize(1);
        assertThat(second.get().getOrders().get(0).getStatus()).isEqualTo("SERVICE_UNAVAILABLE");
    }
}

