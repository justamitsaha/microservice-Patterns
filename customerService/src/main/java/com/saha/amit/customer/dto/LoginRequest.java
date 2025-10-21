package com.saha.amit.customer.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String email; // using email as username
    private String password;
}

