package com.saha.amit.customer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("customers")
public class CustomerEntity {

    @Id
    private Long id;

    private String name;
    private String email;
    private Long createdAt;

    @JsonIgnore
    private String passwordSalt;

    @JsonIgnore
    private String passwordHash;
}
