package com.saha.amit.reactiveOrderService.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.reactiveOrderService.events.OrderEvent;
import com.saha.amit.reactiveOrderService.proto.OrderEventMessage;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    private final Environment env;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic.order}")
    private String orderTopic;

    @Value("${app.kafka.topic.order.retry}")
    private String orderRetryTopic;

    @Value("${app.kafka.topic.order.proto}")
    private String orderProtoTopic;

    @Value("${app.kafka.schema-registry-url}")
    private String schemaRegistryUrl;

    private static final String DEFAULT_GROUP_ID = "order-service";

    // ---------- JSON CONFIG ----------

    @Bean("jsonKafkaSender")
    public KafkaSender<String, OrderEvent> jsonKafkaSender() {
        SenderOptions<String, OrderEvent> senderOptions =
                SenderOptions.<String, OrderEvent>create(commonProducerProps())
                        .withKeySerializer(new StringSerializer())
                        .withValueSerializer((topic, data) -> serializeEvent(data, () -> "JSON serialization failed"));
        return KafkaSender.create(senderOptions);
    }

    @Bean("jsonKafkaReceiver")
    public KafkaReceiver<String, OrderEvent> jsonKafkaReceiver() {
        ReceiverOptions<String, OrderEvent> receiverOptions =
                ReceiverOptions.<String, OrderEvent>create(commonConsumerProps(null))
                        .subscription(List.of(orderTopic))
                        .commitInterval(Duration.ZERO)
                        .commitBatchSize(1)
                        .withKeyDeserializer(new StringDeserializer())
                        .withValueDeserializer((topic, bytes) -> deserializeEvent(bytes, () -> "JSON deserialization failed"));
        return KafkaReceiver.create(receiverOptions);
    }

    @Bean("retryKafkaReceiver")
    public KafkaReceiver<String, OrderEvent> retryKafkaReceiver() {
        String retryGroup = DEFAULT_GROUP_ID + "-retry";
        ReceiverOptions<String, OrderEvent> receiverOptions =
                ReceiverOptions.<String, OrderEvent>create(commonConsumerProps(retryGroup))
                        .subscription(List.of(orderRetryTopic))
                        .commitInterval(Duration.ZERO)
                        .commitBatchSize(1)
                        .withKeyDeserializer(new StringDeserializer())
                        .withValueDeserializer((topic, bytes) -> deserializeEvent(bytes, () -> "Retry JSON deserialization failed"));
        return KafkaReceiver.create(receiverOptions);
    }

    @Bean("byteKafkaSender")
    public KafkaSender<String, byte[]> byteKafkaSender() {
        SenderOptions<String, byte[]> senderOptions =
                SenderOptions.<String, byte[]>create(commonProducerProps())
                        .withKeySerializer(new StringSerializer())
                        .withValueSerializer(new ByteArraySerializer());
        return KafkaSender.create(senderOptions);
    }

    // ---------- PROTOBUF CONFIG ----------

    @Bean("protobufKafkaSender")
    public KafkaSender<String, OrderEventMessage> protobufKafkaSender() {
        Map<String, Object> props = new HashMap<>(commonProducerProps());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put("auto.register.schemas", true);
        return KafkaSender.create(SenderOptions.<String, OrderEventMessage>create(props));
    }

    @Bean("protobufKafkaReceiver")
    public KafkaReceiver<String, OrderEventMessage> protobufKafkaReceiver() {
        Map<String, Object> props = new HashMap<>(commonConsumerProps(DEFAULT_GROUP_ID + "-proto"));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaProtobufDeserializer.class);
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put(KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE, OrderEventMessage.class.getName());
        return KafkaReceiver.create(ReceiverOptions.<String, OrderEventMessage>create(props)
                .subscription(List.of(orderProtoTopic)));
    }

    private Map<String, Object> commonProducerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Objects.requireNonNull(env.getProperty("spring.kafka.bootstrap-servers")));
        props.put(ProducerConfig.ACKS_CONFIG, env.getProperty("spring.kafka.producer.acks", "all"));
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, env.getProperty("spring.kafka.producer.delivery-timeout", "120000"));
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, env.getProperty("spring.kafka.producer.batch-size", "16384"));
        props.put(ProducerConfig.LINGER_MS_CONFIG, env.getProperty("spring.kafka.producer.linger-ms", "10"));
        props.put(ProducerConfig.RETRIES_CONFIG, env.getProperty("spring.kafka.producer.retries", "5"));
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, env.getProperty("spring.kafka.producer.max-in-flight", "5"));
        return props;
    }

    private Map<String, Object> commonConsumerProps(String overrideGroupId) {
        String baseGroup = env.getProperty("spring.kafka.consumer.group-id", DEFAULT_GROUP_ID);
        String resolvedGroup = overrideGroupId != null ? overrideGroupId : baseGroup;

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Objects.requireNonNull(env.getProperty("spring.kafka.bootstrap-servers")));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, resolvedGroup);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, env.getProperty("spring.kafka.consumer.enable-auto-commit", "false"));
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, env.getProperty("spring.kafka.consumer.auto-offset-reset", "earliest"));
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, env.getProperty("spring.kafka.consumer.max-poll-records", "50"));
        return props;
    }

    private byte[] serializeEvent(OrderEvent event, Supplier<String> errorMessage) {
        try {
            return objectMapper.writeValueAsBytes(event);
        } catch (Exception e) {
            throw new IllegalStateException(errorMessage.get(), e);
        }
    }

    private OrderEvent deserializeEvent(byte[] payload, Supplier<String> errorMessage) {
        try {
            return objectMapper.readValue(payload, OrderEvent.class);
        } catch (Exception e) {
            throw new IllegalStateException(errorMessage.get(), e);
        }
    }
}

