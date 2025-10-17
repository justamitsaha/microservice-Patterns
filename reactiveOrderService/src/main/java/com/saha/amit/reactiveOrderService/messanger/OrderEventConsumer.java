package com.saha.amit.reactiveOrderService.messanger;

import com.saha.amit.reactiveOrderService.events.OrderEvent;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
//@RequiredArgsConstructor
public class OrderEventConsumer {

    private static final String RETRY_ATTEMPT_HEADER = "retry-attempt";

    private final KafkaReceiver<String, OrderEvent> kafkaReceiver;
    private final KafkaReceiver<String, OrderEvent> retryKafkaReceiver;
    private final DltPublisher dltPublisher;
    private final RetryEventPublisher retryEventPublisher;
    private final MeterRegistry meterRegistry;

    @Value("${app.kafka.retry.max-attempts:3}")
    private int maxAttempts;

    private Disposable mainSubscription;
    private Disposable retrySubscription;

    public OrderEventConsumer(@Qualifier("jsonKafkaReceiver")KafkaReceiver<String, OrderEvent> kafkaReceiver,
                              @Qualifier("retryKafkaReceiver")KafkaReceiver<String, OrderEvent> retryKafkaReceiver,
                              DltPublisher dltPublisher,
                              RetryEventPublisher retryEventPublisher,
                              MeterRegistry meterRegistry) {
        this.kafkaReceiver = kafkaReceiver;
        this.retryKafkaReceiver = retryKafkaReceiver;
        this.dltPublisher = dltPublisher;
        this.retryEventPublisher = retryEventPublisher;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void start() {
        log.info("Starting OrderEventConsumer with maxAttempts={}", maxAttempts);
        this.mainSubscription = subscribe(kafkaReceiver, false);
        this.retrySubscription = subscribe(retryKafkaReceiver, true);
    }

    @PreDestroy
    public void shutdown() {
        if (mainSubscription != null) {
            mainSubscription.dispose();
        }
        if (retrySubscription != null) {
            retrySubscription.dispose();
        }
    }

    private Disposable subscribe(KafkaReceiver<String, OrderEvent> receiver, boolean isRetryTopic) {
        return receiver.receive()
                .flatMap(record -> processRecord(record, isRetryTopic).thenReturn(record))
                .doOnError(ex -> log.error("Error consuming Kafka events", ex))
                .retryWhen(Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(5)))
                .subscribe();
    }

    private Mono<Void> processRecord(ReceiverRecord<String, OrderEvent> record, boolean isRetryTopic) {
        OrderEvent event = record.value();
        int attempt = resolveAttempt(record, isRetryTopic);

        log.info("Received eventId={} from topic={} partition={} offset={} attempt={} retryTopic={}",
                event.eventId(), record.topic(), record.partition(), record.offset(), attempt, isRetryTopic);

        return handleBusinessLogic(event)
                .doOnSuccess(ignored -> meterRegistry.counter("order.consumer.processed").increment())
                .doOnError(ex -> meterRegistry.counter("order.consumer.failed").increment())
                .onErrorResume(ex -> handleFailure(record, event, attempt, ex))
                .then(Mono.fromRunnable(record.receiverOffset()::acknowledge));
    }

    private Mono<Void> handleBusinessLogic(OrderEvent event) {
        return Mono.defer(() -> {
            if (event.amount() == null || event.amount() <= 0) {
                return Mono.error(new IllegalArgumentException("Invalid amount: " + event.amount()));
            }
            log.info("Processing order event for orderId={} customerId={} amount={}", event.orderId(), event.customerId(), event.amount());
            return Mono.empty();
        });
    }

    private Mono<Void> handleFailure(ReceiverRecord<String, OrderEvent> record,
                                     OrderEvent event,
                                     int attempt,
                                     Throwable ex) {
        log.warn("Failed to process eventId={} attempt={} reason={}", event.eventId(), attempt, ex.getMessage());

        if (attempt >= maxAttempts) {
            log.error("Exhausted retries for eventId={}, routing to DLT", event.eventId());
            return dltPublisher.sendToDlt(
                    event,
                    "consumer",
                    ex.getClass().getSimpleName(),
                    record.topic(),
                    record.partition(),
                    record.offset()
            );
        }

        return retryEventPublisher.scheduleRetry(event, attempt);
    }

    private int resolveAttempt(ReceiverRecord<String, OrderEvent> record, boolean isRetryTopic) {
        if (!isRetryTopic) {
            return 0;
        }

        return Optional.ofNullable(record.headers().lastHeader(RETRY_ATTEMPT_HEADER))
                .map(header -> new String(header.value(), StandardCharsets.UTF_8))
                .map(Integer::parseInt)
                .orElse(1);
    }
}
