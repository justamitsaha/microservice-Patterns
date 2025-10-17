package com.saha.amit.reactiveOrderService.messanger;

import com.saha.amit.reactiveOrderService.events.OrderEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;

import java.time.Duration;

@Slf4j
@Service
public class OrderEventPublisher {

    private final @Qualifier("jsonKafkaSender") KafkaSender<String, OrderEvent> kafkaSender;
    private final DltPublisher dltPublisher;
    private final MeterRegistry meterRegistry;
    private final Counter successCounter;
    private final Counter failureCounter;

    @Value("${app.kafka.topic.order}")
    private String orderTopic;

    public OrderEventPublisher(@Qualifier("jsonKafkaSender") KafkaSender<String, OrderEvent> kafkaSender,
                               DltPublisher dltPublisher,
                               MeterRegistry meterRegistry) {
        this.kafkaSender = kafkaSender;
        this.dltPublisher = dltPublisher;
        this.meterRegistry = meterRegistry;
        this.successCounter = meterRegistry.counter("order.publisher.success");
        this.failureCounter = meterRegistry.counter("order.publisher.failure");
    }

    public Mono<Void> publish(OrderEvent event) {
        SenderRecord<String, OrderEvent, OrderEvent> record =
                SenderRecord.create(new ProducerRecord<>(orderTopic, event.eventId(), event), event);

        return kafkaSender.send(Mono.just(record))
                .timeout(Duration.ofSeconds(10))
                .flatMap(result -> handleResult(result, successCounter, failureCounter))
                .then();
    }

    private Mono<Void> handleResult(SenderResult<OrderEvent> result, Counter successCounter, Counter failureCounter) {
        if (result.exception() == null) {
            successCounter.increment();
            RecordMetadata metadata = result.recordMetadata();
            log.info("Published eventId={} to topic={}, partition={}, offset={}",
                    result.correlationMetadata().eventId(), metadata.topic(), metadata.partition(), metadata.offset());
            return Mono.empty();
        }

        failureCounter.increment();
        OrderEvent event = result.correlationMetadata();
        log.error("Failed to publish eventId={} due to {}", event.eventId(), result.exception().getMessage());
        return dltPublisher.sendToDlt(
                event,
                "producer",
                result.exception().getClass().getSimpleName(),
                orderTopic,
                result.recordMetadata() != null ? result.recordMetadata().partition() : null,
                result.recordMetadata() != null ? result.recordMetadata().offset() : null
        ).then(Mono.error(new IllegalStateException("Kafka send failed for eventId=" + event.eventId())));
    }
}
