package com.saha.amit.customer.service;

import com.saha.amit.customer.dto.CustomerRequest;
import com.saha.amit.customer.dto.LoginRequest;
import com.saha.amit.customer.dto.LoginResponse;
import com.saha.amit.customer.model.CustomerEntity;
import com.saha.amit.customer.repository.CustomerRepository;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class CustomerServiceTest {

    private CustomerRepository repository;
    private WebClient orderWebClient;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        repository = mock(CustomerRepository.class);
        orderWebClient = WebClient.builder().baseUrl("http://localhost").build();
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
    void registrationStoresSaltAndHash() {
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        CustomerRequest req = new CustomerRequest();
        req.setName("Alice");
        req.setEmail("alice@example.com");
        req.setPassword("secret123");

        Mono<CustomerEntity> mono = service.create(req);

        StepVerifier.create(mono)
                .assertNext(saved -> {
                    assertThat(saved.getPasswordSalt()).isNotBlank();
                    assertThat(saved.getPasswordHash()).isNotBlank();
                })
                .verifyComplete();
    }

    @Test
    void updateChangesPasswordWhenProvided() {
        CustomerEntity existing = new CustomerEntity();
        existing.setId(1L);
        existing.setName("Bob");
        existing.setEmail("bob@example.com");
        existing.setPasswordSalt("oldSalt");
        existing.setPasswordHash("oldHash");

        when(repository.findById("id-1")).thenReturn(Mono.just(existing));
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        CustomerRequest req = new CustomerRequest();
        req.setName("Bob");
        req.setEmail("bob@example.com");
        req.setPassword("newPass");

        StepVerifier.create(service.update("1", req))
                .assertNext(updated -> {
                    assertThat(updated.getPasswordSalt()).isNotEqualTo("oldSalt");
                    assertThat(updated.getPasswordHash()).isNotEqualTo("oldHash");
                })
                .verifyComplete();
    }

    @Test
    void loginSuccessWithCorrectPassword() {
        // Prepare stored user with salt+hash by calling create
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        CustomerRequest create = new CustomerRequest();
        create.setName("Carol");
        create.setEmail("carol@example.com");
        create.setPassword("s3cret!");
        CustomerEntity saved = service.create(create).block();

        when(repository.findByEmail("carol@example.com")).thenReturn(Mono.just(saved));

        LoginRequest login = new LoginRequest();
        login.setEmail("carol@example.com");
        login.setPassword("s3cret!");

        StepVerifier.create(service.login(login))
                .assertNext(resp -> {
                    assertThat(resp.isSuccess()).isTrue();
                    assertThat(resp.getMessage()).contains("successful");
                })
                .verifyComplete();
    }

    @Test
    void loginFailsWithBadPassword() {
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        CustomerRequest create = new CustomerRequest();
        create.setName("Dave");
        create.setEmail("dave@example.com");
        create.setPassword("good");
        CustomerEntity saved = service.create(create).block();

        when(repository.findByEmail("dave@example.com")).thenReturn(Mono.just(saved));

        LoginRequest login = new LoginRequest();
        login.setEmail("dave@example.com");
        login.setPassword("bad");

        StepVerifier.create(service.login(login))
                .assertNext(resp -> assertThat(resp.isSuccess()).isFalse())
                .verifyComplete();
    }
}

