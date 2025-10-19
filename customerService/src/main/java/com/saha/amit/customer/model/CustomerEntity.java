package com.saha.amit.customer.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("customers")
public class CustomerEntity {

    @Id
    private String id;

    private String name;
    private String email;
    private Long createdAt;
}

