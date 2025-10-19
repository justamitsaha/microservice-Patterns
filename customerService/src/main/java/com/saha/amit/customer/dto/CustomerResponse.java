package com.saha.amit.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CustomerResponse {
    private String id;
    private String name;
    private String email;
    private Long createdAt;
    private List<OrderResponse> orders; // may be null for non-aggregated responses
}

