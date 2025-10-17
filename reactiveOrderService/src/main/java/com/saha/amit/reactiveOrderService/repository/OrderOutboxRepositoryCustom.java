package com.saha.amit.reactiveOrderService.repository;

import com.saha.amit.reactiveOrderService.model.OrderOutboxEntity;
import reactor.core.publisher.Mono;

public interface OrderOutboxRepositoryCustom {
    Mono<OrderOutboxEntity> insert(OrderOutboxEntity entity);
}
