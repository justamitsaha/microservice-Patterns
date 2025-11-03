package com.saha.amit.customerMvc.dto;

import lombok.Data;

@Data
public class CustomerRequest {
    private String name;
    private String email;
    private String password;
}

