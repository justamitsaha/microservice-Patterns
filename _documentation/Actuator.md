# Spring Boot Actuator Documentation

Spring Boot Actuator provides production-ready features to help you monitor and manage your microservices. This document provides a guide to the endpoints exposed across the Microservice Patterns Playground.

---

## 🚀 Overview of Services and Ports

| Service | Port | Key Actuator Path |
| :--- | :--- | :--- |
| **Discovery Service** | 8761 | `http://localhost:8761/actuator` |
| **Config Service** | 8888 | `http://localhost:8888/actuator` |
| **Order Service** | 8080 | `http://localhost:8080/actuator` |
| **Customer Service** | 8082 | `http://localhost:8082/actuator` |
| **Gateway Service** | 8085 | `http://localhost:8085/actuator` |

---

## 🛠️ Key Endpoints and Usage

### 1. Health Monitoring (`/health`)
Provides the "up/down" status of the service and its dependencies (DB, Kafka, Disk, etc.).
*   **Basic:** `http://localhost:8085/actuator/health`
*   **Detailed:** Shows status of individual components like `r2dbc`, `discoveryComposite`, and `circuitBreakers`.
*   **Circuit Breaker Integration:** On the Gateway and Customer Service, you can see the state of specific breakers:
    *   `http://localhost:8085/actuator/health` (Look for `circuitBreakers` section)

### 2. Metrics & Observability (`/metrics`, `/prometheus`)
Exposes internal application metrics for monitoring performance.
*   **List all metrics:** `http://localhost:8080/actuator/metrics`
*   **Specific Metric:**
    *   HTTP Requests: `http://localhost:8085/actuator/metrics/http.server.requests`
    *   JVM Memory: `http://localhost:8082/actuator/metrics/jvm.memory.used`
    *   Circuit Breaker State: `http://localhost:8085/actuator/metrics/resilience4j.circuitbreaker.state?tag=name:customersCb`
*   **Prometheus:** Scrape-ready metrics format for Grafana dashboards.
    *   `http://localhost:8085/actuator/prometheus`

### 3. Resilience Insights (`/circuitbreakers`, `/retries`, `/bulkheads`)
Specific to services using **Resilience4j** (Gateway and Customer Service).
*   **Circuit Breaker State:**
    *   `http://localhost:8085/actuator/circuitbreakers` (List all)
    *   `http://localhost:8085/actuator/circuitbreakers/customersCb` (Detailed state for `customersCb`)
*   **Events:** See real-time transitions (e.g., when a circuit opens or a retry occurs).
    *   `http://localhost:8085/actuator/circuitbreakerevents`
    *   `http://localhost:8082/actuator/retryevents`

### 4. Dynamic Configuration (`/refresh`, `/busrefresh`)
Used to apply property changes from the Config Server without restarting the service.
*   **Single Instance:** `POST http://localhost:8082/actuator/refresh`
*   **Global Refresh (Cloud Bus):** Triggers a refresh across all services via Kafka.
    *   `POST http://localhost:8888/actuator/busrefresh`

---

## 🔍 Things to Explore

### 🧪 Exercise 1: Watch the Circuit Breaker Trip
1.  Open the Actuator health endpoint: `http://localhost:8085/actuator/health`
2.  Stop the `customer-service`.
3.  Spam the `/customers` API until the circuit breaker trips.
4.  Refresh the health endpoint and observe the `state` changing from `CLOSED` to `OPEN`.

### 🧪 Exercise 2: Inspect Prometheus Metrics
1.  Navigate to `http://localhost:8085/actuator/prometheus`.
2.  Search (Ctrl+F) for `http_server_requests_seconds_count`.
3.  Observe how the count increases as you make requests to the gateway.

### 🧪 Exercise 3: Live Config Refresh
1.  Modify a property in `temp/configurationServer/customer-service.properties` (e.g., `app.simulation.num`).
2.  Commit/Update your config source.
3.  Trigger the bus refresh: `curl -X POST http://localhost:8888/actuator/busrefresh`
4.  Verify the new value via the customer service's success endpoint or its `/env` actuator.
