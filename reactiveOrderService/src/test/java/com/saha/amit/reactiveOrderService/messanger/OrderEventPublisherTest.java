package com.saha.amit.reactiveOrderService.messanger;

import com.saha.amit.reactiveOrderService.events.OrderEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderEventPublisherTest {

    private KafkaSender<String, OrderEvent> kafkaSender;
    private DltPublisher dltPublisher;
    private SimpleMeterRegistry meterRegistry;
    private OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        kafkaSender = mock(KafkaSender.class);
        dltPublisher = mock(DltPublisher.class);
        meterRegistry = new SimpleMeterRegistry();
        publisher = new OrderEventPublisher(kafkaSender, dltPublisher, meterRegistry);
        ReflectionTestUtils.setField(publisher, "orderTopic", "order.events");
    }

    @Test
    void publishSuccessIncrementsCounters() {
        OrderEvent event = OrderEvent.create("order-1", "cust-1", 10.0, "PLACED");

        SenderResult<OrderEvent> result = mock(SenderResult.class);
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("order.events", 0), 0, 0, System.currentTimeMillis(), Long.valueOf(0), 0, 0);

        when(result.exception()).thenReturn(null);
        when(result.recordMetadata()).thenReturn(metadata);
        when(result.correlationMetadata()).thenReturn(event);
        when(kafkaSender.send(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Publisher<SenderRecord<String, OrderEvent, OrderEvent>> publisherParam = invocation.getArgument(0);
            return Flux.from(publisherParam).map(record -> result);
        });

        StepVerifier.create(publisher.publish(event))
                .verifyComplete();

        assertThat(meterRegistry.counter("order.publisher.success").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("order.publisher.failure").count()).isZero();
    }

    @Test
    void publishFailureRoutesToDlt() {
        OrderEvent event = OrderEvent.create("order-1", "cust-1", 10.0, "PLACED");

        SenderResult<OrderEvent> result = mock(SenderResult.class);
        when(result.exception()).thenReturn(new RuntimeException("boom"));
        when(result.correlationMetadata()).thenReturn(event);
        when(kafkaSender.send(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Publisher<SenderRecord<String, OrderEvent, OrderEvent>> publisherParam = invocation.getArgument(0);
            return Flux.from(publisherParam).map(record -> result);
        });
        when(dltPublisher.sendToDlt(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(publisher.publish(event))
                .expectError(IllegalStateException.class)
                .verify();

        assertThat(meterRegistry.counter("order.publisher.success").count()).isZero();
        assertThat(meterRegistry.counter("order.publisher.failure").count()).isEqualTo(1.0);
        verify(dltPublisher).sendToDlt(eq(event), eq("producer"), eq("RuntimeException"), any(), any(), any());
    }
}
