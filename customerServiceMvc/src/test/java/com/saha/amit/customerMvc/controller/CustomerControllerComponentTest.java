package com.saha.amit.customerMvc.controller;

import com.saha.amit.customerMvc.dto.CustomerRequest;
import com.saha.amit.customerMvc.dto.CustomerResponse;
import com.saha.amit.customerMvc.model.CustomerEntity;
import com.saha.amit.customerMvc.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CustomerController.class)
class CustomerControllerComponentTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    CustomerService service;

    @BeforeEach
    void setup() {}

    @Test
    void getNotFound() throws Exception {
        org.mockito.Mockito.doReturn(java.util.concurrent.CompletableFuture.completedFuture(Optional.empty()))
                .when(service).getWithOrders(Mockito.eq("x"), Mockito.anyString());
        mvc.perform(get("/customers/x")).andExpect(status().isNotFound());
    }

    @Test
    void getOk() throws Exception {
        CustomerResponse resp = new CustomerResponse("c1","N","e@x", Instant.now().toEpochMilli(), List.of());
        org.mockito.Mockito.doReturn(java.util.concurrent.CompletableFuture.completedFuture(Optional.of(resp)))
                .when(service).getWithOrders(Mockito.eq("c1"), Mockito.anyString());
        mvc.perform(get("/customers/c1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("c1"));
    }

    @Test
    void registerOk() throws Exception {
        CustomerEntity e = new CustomerEntity(); e.setId("c1"); e.setName("A"); e.setEmail("a@b"); e.setCreatedAt(Instant.now().toEpochMilli());
        when(service.create(any(CustomerRequest.class))).thenReturn(e);
        mvc.perform(post("/customers").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"A\",\"email\":\"a@b\",\"password\":\"p\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("c1"));
    }
}
