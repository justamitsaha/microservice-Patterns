package com.saha.amit.customer.repository;

import com.saha.amit.customer.model.CustomerEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends ReactiveCrudRepository<CustomerEntity, String> {
}

