package com.saha.amit.reactiveOrderService.messanger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.reactiveOrderService.events.OrderEvent;
import com.saha.amit.reactiveOrderService.model.OrderOutboxEntity;
import com.saha.amit.reactiveOrderService.model.OutboxStatus;
import com.saha.amit.reactiveOrderService.repository.OrderOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.Disposable;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OrderOutboxRepository outboxRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${app.kafka.outbox.poll-interval:PT1S}")
    private Duration pollInterval;

    @Value("${app.kafka.outbox.batch-size:50}")
    private int batchSize;

    @Value("${app.kafka.outbox.max-attempts:5}")
    private int maxAttempts;

    private Disposable subscription;

    @PostConstruct
    public void start() {
        log.info("Starting OutboxPublisher with pollInterval={} batchSize={}", pollInterval, batchSize);
        subscription = Flux.interval(Duration.ZERO, pollInterval)
                .flatMap(tick -> outboxRepository.findNextBatch(Instant.now(), batchSize))
                .flatMap(this::publishOutboxRecord, 1)
                .onErrorContinue((ex, record) -> log.error("Outbox dispatch errored: {}", ex.getMessage()))
                .subscribe();
    }

    @PreDestroy
    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }

    private Mono<Void> publishOutboxRecord(OrderOutboxEntity entity) {
        return Mono.defer(() -> {
            OrderEvent event = deserialize(entity);
            return orderEventPublisher.publish(event)
                    .then(updateStatus(entity, OutboxStatus.PUBLISHED, null))
                    .doOnSuccess(ignored -> meterRegistry.counter("order.outbox.published").increment())
                    .doOnError(ex -> meterRegistry.counter("order.outbox.failed").increment())
                    .onErrorResume(ex -> updateForRetry(entity, ex));
        });
    }

    private OrderEvent deserialize(OrderOutboxEntity entity) {
        try {
            return objectMapper.readValue(entity.getPayload(), OrderEvent.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize outbox payload for id=" + entity.getId(), e);
        }
    }

    private Mono<Void> updateStatus(OrderOutboxEntity entity, OutboxStatus status, String lastError) {
        entity.setStatus(status);
        entity.setLastError(lastError);
        entity.setAvailableAt(Instant.now());
        entity.setAttempts(entity.getAttempts() + 1);
        return outboxRepository.save(entity).then();
    }

    private Mono<Void> updateForRetry(OrderOutboxEntity entity, Throwable ex) {
        entity.setAttempts(entity.getAttempts() + 1);
        entity.setLastError(ex.getMessage());
        entity.setStatus(OutboxStatus.FAILED);

        if (entity.getAttempts() >= maxAttempts) {
            log.error("Outbox entry {} exceeded max attempts, marking as FAILED", entity.getId());
            entity.setAvailableAt(Instant.now().plus(Duration.ofHours(1)));
            return outboxRepository.save(entity).then();
        }

        Duration backoff = Duration.ofSeconds((long) Math.min(60, Math.pow(2, entity.getAttempts())));
        entity.setAvailableAt(Instant.now().plus(backoff));
        log.warn("Outbox entry {} failed (attempt {}), retrying after {}", entity.getId(), entity.getAttempts(), backoff);
        return outboxRepository.save(entity).then();
    }
}
