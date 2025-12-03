package com.saha.amit.customer.integration;

import com.saha.amit.customer.model.CustomerEntity;
import com.saha.amit.customer.repository.CustomerRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;

import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class CustomerAggregationIT {

    @Autowired
    WebTestClient client;

    @MockitoBean
    CustomerRepository repository; // avoid real DB

    static MockWebServer mockOrders;

    @BeforeAll
    static void startServer() throws IOException {
        mockOrders = new MockWebServer();
        mockOrders.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        mockOrders.shutdown();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public WebClient orderWebClient() {
            String base = mockOrders.url("/").toString();
            return WebClient.builder().baseUrl(base).build();
        }
    }

    @Test
    void getCustomerAggregatesOrders() {
        CustomerEntity e = new CustomerEntity();
        e.setId(1L); e.setName("T"); e.setEmail("t@e.com"); e.setCreatedAt(System.currentTimeMillis());
        when(repository.findById("c-1")).thenReturn(Mono.just(e));

        mockOrders.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[ { \"orderId\":\"1\",\"customerId\":\"c-1\",\"amount\":42.0,\"status\":\"PLACED\" } ]")
                .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

        client.get().uri("/customers/{id}", "1")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$.id").isEqualTo("1")
                .jsonPath("$.orders[0].orderId").isEqualTo("1");
    }
}

