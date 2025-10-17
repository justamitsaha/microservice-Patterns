package com.saha.amit.reactiveOrderService.repository;

import com.saha.amit.reactiveOrderService.model.OrderEntity;
import reactor.core.publisher.Mono;

public interface OrderRepositoryCustom {
    Mono<OrderEntity> insert(OrderEntity entity);
}
