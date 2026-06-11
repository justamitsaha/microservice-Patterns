# Microservice Patterns Playground

This repository contains a high-level overview of a reactive, event-driven microservices architecture built using **Spring Boot WebFlux** and **Spring Cloud**. The project demonstrates key distributed system patterns such as service discovery, centralized configuration, resilient communication, and the transactional outbox pattern.

This document serves as a blueprint for system design and cloud migration (e.g., to AWS / Kubernetes).

---

## 🏗️ System Architecture Overview
The system is designed as a set of autonomous services that communicate via synchronous REST (for queries) and asynchronous events (for state changes). It uses an **API Gateway** as the single entry point, protecting internal services with rate limiting and circuit breakers.

---

## 📦 Containerized Components
The architecture consists of three logical groups of containers:

### 1. Core Microservices (Java/Spring Boot)
| Container | Responsibility | Technology |
| :--- | :--- | :--- |
| **discovery-service** | Service registration and heartbeats. | Netflix Eureka |
| **config-service** | Centralized property management with Git backend. | Spring Cloud Config |
| **gateway-service** | Request routing, security, and global rate limiting. | Spring Cloud Gateway |
| **customer-service** | Manages user profiles and aggregates order history. | Reactive Spring Data R2DBC |
| **order-service** | Lifecycle of orders using Transactional Outbox. | Reactive Spring Data R2DBC |
| **web-app** | Single Page Application (SPA) for end-user interaction. | Angular |

### 2. Infrastructure Containers (Backing Services)
*   **Database (MySQL):** Relational storage for order data.
*   **Database (Postgres):** Relational storage for customer data.
*   **Message Broker (Kafka):** Distributed event streaming for order events and config bus.
*   **In-Memory Store (Redis):** Distributed rate limiting and session-related data.
*   **Schema Registry:** Management of Avro/Protobuf schemas for event serialization.
*   **Coordination (Zookeeper):** Coordination for the Kafka cluster state.

### 3. Observability Containers (Telemetry Stack)
*   **OTel Collector:** Central pipeline for receiving and exporting telemetry data.
*   **Prometheus:** Time-series database for metrics aggregation.
*   **Grafana:** Unified visualization dashboard for metrics, logs, and traces.
*   **Tempo / Jaeger:** Storage and querying for distributed traces.
*   **Loki:** Log aggregation and storage.
*   **Promtail:** Log collector that ships container logs to Loki.

---

## 🚀 Deployment Archetypes
The system can be deployed in different configurations depending on the environment (e.g., K8s namespaces or clusters).

### A. Full Observability Archetype
This configuration deploys the **complete ecosystem** (All 18+ containers listed above).
- **Goal:** Comprehensive monitoring, tracing, and logging for production-like environments or performance testing.
- **Connectivity:** All microservices are configured to export telemetry data to the OTel Collector.

### B. Minimal Core Archetype
This configuration deploys only the **essential components** required for functionality.
- **Core Containers:** Microservices + Infrastructure (Kafka, Redis, Databases).
- **Excluded Containers:** The entire Observability Stack.
- **Optimization:** Microservices have telemetry export (`otlp.endpoint`) disabled to save CPU and Memory resources. This is ideal for lightweight development environments.

---

## 📂 Detailed Technical Documentation
For deep-dives into specific implementation details, please refer to the files in the `_documentation/` folder:

*   [**Business Logic:**](./_documentation/CustomerService_Logic.md) Functional breakdown of services.
*   [**Resilience & Fallbacks:**](./_documentation/CustomerService_Fallback.md) Circuit breakers, retries, and timeouts.
*   [**Rate Limiting:**](./_documentation/RateLimiting.md) Multi-tier protection using cookies and IP.
*   [**Centralized Config:**](./_documentation/ConfigServer.md) Setup for dynamic property updates and secrets.
*   [**Observability:**](./_documentation/Actuator.md) Guide to Actuator endpoints and metrics exploration.
*   [**Distributed Tracing:**](./_documentation/Distributed_tracing.md) Detailed tracing architecture.
