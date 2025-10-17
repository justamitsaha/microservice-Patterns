package com.saha.amit.reactiveOrderService.repository;

import com.saha.amit.reactiveOrderService.model.OrderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final R2dbcEntityTemplate template;

    @Override
    public Mono<OrderEntity> insert(OrderEntity entity) {
        return template.insert(entity);
    }
}
