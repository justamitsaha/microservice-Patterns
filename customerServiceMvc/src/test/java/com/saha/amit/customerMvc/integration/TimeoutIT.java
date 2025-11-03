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

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TimeoutIT {

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
    void requestTimesOutAndFallsBack() {
        CustomerEntity e = new CustomerEntity();
        e.setId("c1"); e.setName("n"); e.setEmail("e@x"); e.setCreatedAt(Instant.now().toEpochMilli());
        when(repository.findById("c1")).thenReturn(Optional.of(e));

        // Delay body for 3s which is > RestTemplate 2s timeout
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]").setBodyDelay(3, java.util.concurrent.TimeUnit.SECONDS));

        String base = server.url("/").toString();
        long start = System.nanoTime();
        var resp = service.getWithOrders("c1", base);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(resp).isPresent();
        assertThat(resp.get().getOrders()).hasSize(1);
        assertThat(resp.get().getOrders().get(0).getStatus()).isEqualTo("SERVICE_UNAVAILABLE");
        // Ensure timeout/fallback returned promptly (< 3s)
        assertThat(elapsedMs).isLessThan(3000);
    }
}

