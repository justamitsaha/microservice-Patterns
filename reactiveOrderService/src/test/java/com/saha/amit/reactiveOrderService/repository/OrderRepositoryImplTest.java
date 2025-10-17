package com.saha.amit.reactiveOrderService.repository;

import com.saha.amit.reactiveOrderService.model.OrderEntity;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderRepositoryImplTest {

    @Test
    void insertDelegatesToTemplate() {
        R2dbcEntityTemplate template = mock(R2dbcEntityTemplate.class);
        OrderRepositoryImpl repository = new OrderRepositoryImpl(template);
        OrderEntity entity = new OrderEntity();
        when(template.insert(any(OrderEntity.class))).thenReturn(Mono.just(entity));

        StepVerifier.create(repository.insert(entity))
                .expectNext(entity)
                .verifyComplete();

        verify(template).insert(entity);
        assertThat(entity).isNotNull();
    }
}
