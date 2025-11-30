package com.saha.amit.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CustomerResponse {
    private Long id;
    private String name;
    private String email;
    private Long createdAt;
    private List<OrderResponse> orders; // maybe null for non-aggregated responses
}

