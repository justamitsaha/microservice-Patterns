package com.saha.amit.customer.repository;

import com.saha.amit.customer.model.CustomerEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

@DataR2dbcTest
public class CustomerRepositoryIT {

    @Autowired
    DatabaseClient db;

    @Autowired
    CustomerRepository repository;

    @BeforeEach
    void initSchema() {
        db.sql("DROP TABLE IF EXISTS customers").then().block();
        db.sql("""
                        CREATE TABLE IF NOT EXISTS customers (
                          id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                          name            VARCHAR(255) NOT NULL,
                          email           VARCHAR(255) NOT NULL,
                          created_at      BIGINT NOT NULL,
                          password_salt   VARCHAR(255) NOT NULL,
                          password_hash   VARCHAR(255) NOT NULL,
                          UNIQUE (email)
                        )
                """)
                .then().block();
        db.sql("CREATE INDEX idx_customers_created_at ON customers (created_at);").then().block();
    }

    @Test
    void saveAndFindByEmailWorks() {
        CustomerEntity e = new CustomerEntity();
        e.setId(1L);
        e.setName("RepoUser");
        e.setEmail("repo@example.com");
        e.setCreatedAt(Instant.now().toEpochMilli());
        e.setPasswordSalt("salt");
        e.setPasswordHash("hash");

        Mono<CustomerEntity> saved = repository.save(e);

        StepVerifier.create(saved.flatMap(s -> repository.findByEmail("repo@example.com")))
                .assertNext(found -> {
                    org.assertj.core.api.Assertions.assertThat(found.getName()).isEqualTo("RepoUser");
                    org.assertj.core.api.Assertions.assertThat(found.getPasswordHash()).isEqualTo("hash");
                })
                .verifyComplete();
    }
}

