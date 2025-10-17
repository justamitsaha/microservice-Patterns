package com.saha.amit.reactiveOrderService.repository;

import com.saha.amit.reactiveOrderService.model.OrderOutboxEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
class OrderOutboxRepositoryImpl implements OrderOutboxRepositoryCustom {

    private final R2dbcEntityTemplate template;

    @Override
    public Mono<OrderOutboxEntity> insert(OrderOutboxEntity entity) {
        return template.insert(entity);
    }
}
