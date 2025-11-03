package com.saha.amit.customerMvc.integration;

import com.saha.amit.customerMvc.model.CustomerEntity;
import com.saha.amit.customerMvc.repository.CustomerRepository;
import com.saha.amit.customerMvc.service.CustomerService;
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
        "resilience4j.retry.instances.orderService.maxAttempts=3",
        "resilience4j.retry.instances.orderService.waitDuration=10ms"
})
class RetryIT {

    @Autowired
    CustomerService service;

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
    void retriesOnFailure() throws Exception {
        CustomerEntity e = new CustomerEntity();
        e.setId("c1"); e.setName("n"); e.setEmail("e@x"); e.setCreatedAt(Instant.now().toEpochMilli());
        when(repository.findById("c1")).thenReturn(Optional.of(e));

        // Enqueue 3 failures to match maxAttempts=3
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));

        String base = server.url("/").toString();
        service.getWithOrders("c1", base);

        // Expect exactly 3 requests attempted
        Thread.sleep(100); // small delay to let async scheduling complete
        assertThat(server.getRequestCount()).isEqualTo(3);
    }
}

