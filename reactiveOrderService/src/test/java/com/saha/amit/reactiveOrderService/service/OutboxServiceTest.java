package com.saha.amit.reactiveOrderService.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.reactiveOrderService.model.OrderEntity;
import com.saha.amit.reactiveOrderService.model.OrderOutboxEntity;
import com.saha.amit.reactiveOrderService.repository.OrderOutboxRepository;
import com.saha.amit.reactiveOrderService.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxServiceTest {

    private OrderRepository orderRepository;
    private OrderOutboxRepository orderOutboxRepository;
    private TransactionalOperator transactionalOperator;
    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderOutboxRepository = mock(OrderOutboxRepository.class);
        transactionalOperator = mock(TransactionalOperator.class);
        ObjectMapper objectMapper = new ObjectMapper();

        outboxService = new OutboxService(orderRepository, orderOutboxRepository, transactionalOperator, objectMapper);

        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void persistOrderAndOutboxStoresBothEntities() {
        when(orderRepository.insert(any(OrderEntity.class)))
                .thenAnswer(invocation -> {
                    OrderEntity entity = invocation.getArgument(0);
                    entity.setCreatedAt(Instant.now().toEpochMilli());
                    return Mono.just(entity);
                });

        when(orderOutboxRepository.insert(any(OrderOutboxEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(outboxService.persistOrderAndOutbox("cust-42", 75.5))
                .expectNextMatches(event ->
                        event.customerId().equals("cust-42") &&
                                event.amount().equals(75.5) &&
                                event.status().equals("PLACED"))
                .verifyComplete();

        verify(orderRepository).insert(any(OrderEntity.class));
        ArgumentCaptor<OrderOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(OrderOutboxEntity.class);
        verify(orderOutboxRepository).insert(outboxCaptor.capture());

        OrderOutboxEntity outboxEntity = outboxCaptor.getValue();
        assertThat(outboxEntity.getAggregateId()).isNotBlank();
        assertThat(outboxEntity.getPayload()).contains("\"customerId\":\"cust-42\"");
        assertThat(outboxEntity.getPayload()).contains("\"amount\":75.5");
        assertThat(outboxEntity.getStatus()).isNotNull();
    }

    @Test
    void persistOrderHandlesSerializationFailure() throws JsonProcessingException {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        outboxService = new OutboxService(orderRepository, orderOutboxRepository, transactionalOperator, failingMapper);
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.insert(any(OrderEntity.class))).thenReturn(Mono.just(new OrderEntity()));
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});

        assertThatThrownBy(() -> outboxService.persistOrderAndOutbox("cust", 10.0).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to serialize order event");
    }
}
