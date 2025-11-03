package com.saha.amit.customerMvc.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Data
public class CustomerEntity {
    @Id
    @Column(length = 36)
    private String id;

    private String name;
    private String email;

    @Column(name = "created_at")
    private Long createdAt;

    @JsonIgnore
    @Column(name = "password_salt")
    private String passwordSalt;

    @JsonIgnore
    @Column(name = "password_hash")
    private String passwordHash;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now().toEpochMilli();
    }
}

