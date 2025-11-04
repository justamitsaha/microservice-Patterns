package com.saha.amit.customerMvc.service;

import com.saha.amit.customerMvc.dto.CustomerRequest;
import com.saha.amit.customerMvc.dto.CustomerResponse;
import com.saha.amit.customerMvc.dto.LoginRequest;
import com.saha.amit.customerMvc.dto.OrderResponse;
import com.saha.amit.customerMvc.model.CustomerEntity;
import com.saha.amit.customerMvc.repository.CustomerRepository;
import com.saha.amit.customerMvc.util.CustomerServiceUtil;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CustomerServiceUnitTest {

    private CustomerRepository repository;
    private CustomerService service;
    private CustomerServiceUtil util;
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
    void setup() {
        repository = Mockito.mock(CustomerRepository.class);
        util = new CustomerServiceUtil("oycBHAYRCc8fMxKkRVx9FA4EC+pWAgmeRGxQFbLGb5Y=", 60000, 120000);
        RestTemplate rt = new RestTemplate();
        service = new CustomerService(repository, rt, util);
    }

    @Test
    void registrationStoresSaltAndHash() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CustomerRequest req = new CustomerRequest();
        req.setName("A"); req.setEmail("a@b"); req.setPassword("p");
        CustomerEntity e = service.create(req);
        assertThat(e.getPasswordSalt()).isNotBlank();
        assertThat(e.getPasswordHash()).isNotBlank();
    }

    @Test
    void loginSuccessAndFailure() {
        // Create user
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CustomerRequest req = new CustomerRequest();
        req.setName("U"); req.setEmail("u@e"); req.setPassword("p");
        CustomerEntity saved = service.create(req);
        when(repository.findByEmail("u@e")).thenReturn(Optional.of(saved));

        // Success
        LoginRequest lr = new LoginRequest(); lr.setEmail("u@e"); lr.setPassword("p");
        Map<String, String> ok = service.login(lr);
        assertThat(ok.get("message")).contains("successful");
        assertThat(ok.get("accessToken")).isNotBlank();

        // Failure
        lr.setPassword("bad");
        Map<String, String> bad = service.login(lr);
        assertThat(bad.get("message")).contains("Invalid");
    }

    // Fallback is covered in integration tests where AOP proxies are active.
}
