package com.saha.amit.customer.controller;

import com.saha.amit.customer.dto.CustomerRequest;
import com.saha.amit.customer.dto.CustomerResponse;
import com.saha.amit.customer.model.CustomerEntity;
import com.saha.amit.customer.repository.CustomerRepository;
import com.saha.amit.customer.service.CustomerService;
import com.saha.amit.customer.util.CustomerServiceUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Component tests around controller mappings and statuses. */
class CustomerControllerComponentTest {

    private CustomerService service;
    private CustomerRepository repository;
    private CustomerServiceUtil util;
    private WebTestClient client;

    @BeforeEach
    void setup() {
        service = Mockito.mock(CustomerService.class);
        repository = Mockito.mock(CustomerRepository.class);
        util = Mockito.mock(CustomerServiceUtil.class);
        CustomerController controller = new CustomerController(service, repository, util);
        client = WebTestClient.bindToController(controller).build();
    }

    @Test
    void getNotFoundReturns404() {
        when(service.getWithOrders("missing")).thenReturn(Mono.empty());

        client.get().uri("/customers/missing")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void updateNotFoundReturns404() {
        when(service.update(eq("id-1"), any(CustomerRequest.class))).thenReturn(Mono.empty());

        client.put().uri("/customers/id-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"N\",\"email\":\"e\"}")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteNotFoundReturns404() {
        when(service.findById("id-2")).thenReturn(Mono.empty());

        client.delete().uri("/customers/id-2")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getSuccessReturnsCustomerResponse() {
        CustomerResponse resp = new CustomerResponse("c1","U","u@e", Instant.now().toEpochMilli(), java.util.List.of());
        when(service.getWithOrders("c1")).thenReturn(Mono.just(resp));

        client.get().uri("/customers/c1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("c1");
    }
}

