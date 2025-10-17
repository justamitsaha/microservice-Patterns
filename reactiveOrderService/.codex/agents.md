# Agent Profile: Enterprise Java Developer (Reactive Kafka Focus)

## Overview
The user is an enterprise Java developer working on a Spring Boot project using Reactor Flux and Reactive Kafka.  
They already have an API that publishes to Kafka and want to enhance it with advanced, production-grade Kafka features.

## Current Code Layout
The existing Spring Boot project follows this structure:
- **configuration** → Contains general configuration classes (e.g., GAFA config, Kafka config, or other framework configs).
- **controller** → Contains `OrderController`, which handles HTTP requests and publishes to Kafka.
- **dto** → Holds request and response classes for external communication (incoming/outgoing API payloads).
- **event** → Contains event model classes that represent messages to be published to Kafka.
- **messenger** → Contains reactive Kafka producers and consumers for event handling.
- **model** → Holds database entity classes (mapped via R2DBC or JPA, depending on setup).
- **service** → Contains business service classes for handling application logic.
- **resources** → Holds `application.yml` or `application.properties` and other resource files.
- **kafka.sh** → A documentation script for Kafka setup, topic creation, and test commands.

## Goals
- Enhance Kafka handling with schema management, resilience, and observability.
- Introduce enterprise patterns like Outbox, DLQ, and retry topics.
- Keep the system fully reactive end-to-end.

## Learning Focus Areas (1-liner summary)
1. **Kafka Core Concepts** – Topics, partitions, offsets, producers, consumers, and consumer groups.
2. **Kafka Configuration** – Producer/consumer tuning (acks, retries, batching, compression).
3. **Spring & Reactive Kafka** – Use `ReactiveKafkaProducerTemplate` and `ReactiveKafkaConsumerTemplate`.
4. **Reactive Backpressure** – Manage flow control using Project Reactor operators.
5. **Schema Management** – Learn Avro, Protobuf, and Confluent Schema Registry.
6. **Error Handling & Retries** – Implement DLQ, retry topics, and non-blocking recovery.
7. **Observability** – Use Micrometer, Prometheus, and Sleuth/Zipkin for metrics and tracing.
8. **Transactional Messaging** – Ensure exactly-once delivery and idempotent operations.
9. **Security** – Set up SSL/SASL, ACLs, and authentication mechanisms.
10. **Performance Tuning** – Optimize partition strategy, parallelism, and reactive pipelines.
11. **Streaming & Processing** – Learn Kafka Streams and ksqlDB for data transformation.
12. **Cloud Deployment** – Run Kafka on Kubernetes, GKE, or Confluent Cloud.
13. **Event-Driven Design** – Apply event sourcing and CQRS architectural patterns.
14. **Testing** – Use Testcontainers and Embedded Kafka for integration and reactive testing.
15. **Outbox Pattern** – Implement reliable event publishing from database to Kafka using transactional outbox.

## Expected Output from Codex
- Generate or refactor code aligned with the above goals and current project structure.
- Provide concise, context-aware examples for any of the listed learning areas.
- Suggest best practices for enterprise-grade Kafka and reactive system design.

## Documentation Instructions
Codex should ensure that a complete and up-to-date `README.md` exists in the project root.

### README.md should include:
1. **Project Overview** – High-level description of what the application does (Reactive Spring Boot app publishing and consuming Kafka events).
2. **Architecture Summary** – Explain the key components and their roles (Controller, DTO, Event, Messenger, Model, Service, etc.).
3. **Tech Stack** – Java 21, Spring Boot (WebFlux), Reactor, Reactive Kafka, MySQL/Postgres, Docker/Kafka.
4. **How It Works** – Flow of a request → controller → service → event publish → consumer.
5. **Kafka Setup Guide** – Reference to `kafka.sh` and instructions to start local Kafka for testing.
6. **Configuration** – Overview of `application.yml` and important environment variables.
7. **Running the Application** – Commands to build and run locally (e.g., `./mvnw spring-boot:run`).
8. **Extending Features** – Notes about Outbox pattern, retry topics, schema registry, and observability.
9. **Future Enhancements** – Potential enterprise features like CQRS, Kafka Streams, and ksqlDB.
10. **Contributing** – Guidelines for adding new producers/consumers or configuration.

Codex should:
- Create or update `README.md` whenever significant project changes occur.
- Keep documentation concise, professional, and in Markdown format.
- Follow the structure listed above unless overridden by the user.
