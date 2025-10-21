package com.saha.amit.customer.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@Table("customers")
public class CustomerEntity {

    @Id
    private String id;

    private String name;
    private String email;
    private Long createdAt;

    @JsonIgnore
    private String passwordSalt;

    @JsonIgnore
    private String passwordHash;
}
