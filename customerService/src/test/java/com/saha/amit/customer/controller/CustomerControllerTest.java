package com.saha.amit.customer.controller;

import com.saha.amit.customer.dto.CustomerRequest;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class CustomerControllerTest {

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
    void registerReturnsCustomer() {
        CustomerEntity e = new CustomerEntity();
        e.setId(1L); e.setName("Alice"); e.setEmail("alice@example.com");
        when(service.create(any())).thenReturn(Mono.just(e));

        client.post().uri("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Alice\",\"email\":\"alice@example.com\",\"password\":\"p\"}")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1L)
                .jsonPath("$.name").isEqualTo("Alice");

        verify(service).create(any(CustomerRequest.class));
    }

    @Test
    void listReturnsFlux() {
        when(service.findAll()).thenReturn(Flux.empty());

        client.get().uri("/customers")
                .exchange()
                .expectStatus().is2xxSuccessful();

        verify(service).findAll();
    }

    @Test
    void loginEndpointWorks() {
        // Build a user with a valid salt+hash for password "x"
        String salt = java.util.Base64.getEncoder().encodeToString("salt-123456789012".getBytes());
        String hash = CustomerServiceUtil.hashPassword("x", salt);
        CustomerEntity user = new CustomerEntity();
        user.setId(1L);
        user.setEmail("a@b.com");
        user.setPasswordSalt(salt);
        user.setPasswordHash(hash);

        when(repository.findByEmail("a@b.com")).thenReturn(Mono.just(user));
        when(util.generateAccessToken("1", "a@b.com")).thenReturn("token");
        when(util.generateRefreshToken("1", "a@b.com")).thenReturn("rtoken");
        // Validate token returns minimal Claims
        io.jsonwebtoken.impl.DefaultClaims claims = new io.jsonwebtoken.impl.DefaultClaims();
        claims.setSubject("u1");
        claims.put("email", "a@b.com");
        when(util.validateToken("token")).thenReturn(claims);

        client.post().uri("/customers/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"a@b.com\",\"password\":\"x\"}")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectHeader().exists("Set-Cookie")
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("token")
                .jsonPath("$.message").isEqualTo("Login successful");
    }
}
