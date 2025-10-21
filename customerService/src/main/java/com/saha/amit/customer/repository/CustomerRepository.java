package com.saha.amit.customer.repository;

import com.saha.amit.customer.model.CustomerEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface CustomerRepository extends ReactiveCrudRepository<CustomerEntity, String> {
    Mono<CustomerEntity> findByEmail(String email);
}
