# Microservice Patterns Playground

This repository demonstrates microservice patterns on Spring Boot WebFlux with resilience, discovery, gateway, and centralized configuration.

## Architecture
- reactiveOrderService
  - Reactive order domain service with R2DBC persistence and Kafka outbox, retry, and DLQ flows.
  - Exposes HTTP APIs to place and query orders.
  - Registers with Eureka and loads config from the Config Server.
- customerService
  - Reactive customer domain service with CRUD persistence.
  - GET-by-id aggregates orders from the order service using a load-balanced WebClient (`http://order-service`).
  - Resilience: circuit breaker, retry, bulkhead, and timeouts (Resilience4j) with Prometheus metrics.
  - The WebClient bean is `@RefreshScope` so downstream base URL and other properties refresh at runtime.
- gatewayService
  - Spring Cloud Gateway using service discovery locator for dynamic routes (`lb://order-service`, `lb://customer-service`).
- discoveryService
  - Eureka server for service discovery.
- configService
  - Spring Cloud Config Server (native mode) for centralized configuration.
  - Participates in Spring Cloud Bus (Kafka) for distributed config refresh.

## Tech Stack
- Java 21+, Spring Boot 3 (WebFlux, Actuator)
- Project Reactor (Mono/Flux)
- R2DBC with MySQL
- Micrometer metrics (Prometheus ready) + Micrometer Tracing (OpenTelemetry bridge)
- Spring Cloud: Gateway, Eureka, Config, Bus (Kafka)
- Resilience4j: CircuitBreaker, Retry, Bulkhead, TimeLimiter
- OpenAPI via springdoc
- Kafka (reactive) in order service with outbox, retry, and DLT

## How It Works
1. Client places an order via `POST /orders` (order service). The order is persisted and an outbox record is created for Kafka publishing.
2. Clients can fetch orders via `GET /orders`, `GET /orders?customerId=...`, or `GET /orders/{orderId}`.
3. Clients manage customers via CRUD. `GET /customers/{id}` loads the customer and calls `GET /orders?customerId=...` from the order service to include orders in the response.
4. Gateway routes `/orders/**` and `/customers/**` via service discovery.
5. All services pull their configuration from Config Server; changes can be refreshed live using the refresh endpoints.
6. Traces are exported via OpenTelemetry OTLP; metrics are exposed at `/actuator/prometheus`.

## Services
### Order Service
- Path: `reactiveOrderService`
- Run: `cd reactiveOrderService && ./mvnw spring-boot:run`
- Key endpoints:
  - `POST /orders`
  - `GET /orders`
  - `GET /orders?customerId={id}`
  - `GET /orders/{orderId}`
- Discovery ID: `order-service`; reads centralized config.

### Customer Service
- Path: `customerService`
- Run: `cd customerService && ./mvnw spring-boot:run`
- Key endpoints:
  - `POST /customers`
  - `GET /customers`
  - `GET /customers/{id}` (includes orders)
  - `PUT /customers/{id}`
  - `DELETE /customers/{id}`
- Resilience: timeout + circuit breaker + retry + bulkhead; fallback returns a placeholder order with status `SERVICE_UNAVAILABLE`.
- Discovery ID: `customer-service`; reads centralized config.

### API Gateway
- Path: `gatewayService`
- Run: `cd gatewayService && ./mvnw spring-boot:run`
- Routing is defined via a Spring bean RouteLocator.
  - See: `gatewayService/src/main/java/com/saha/amit/gateway/config/GatewayRoutesConfig.java:1`
  - Routes:
    - `/orders/**` -> `lb://order-service`
    - `/customers/**` -> `lb://customer-service`
- Discovery locator is disabled in `application.yml` to avoid conflicts with bean routes.

Filters applied
- Headers: Adds `X-From-Gateway: true` to outbound requests and `X-Gateway: spring-cloud-gateway` to responses.
- Retry: Retries upstream on 5xx (3 times orders, 2 times customers).
- Circuit Breaker: Uses Spring Cloud CircuitBreaker (Resilience4j) with fallbacks:
  - Orders fallback: `forward:/fallback/orders`
  - Customers fallback: `forward:/fallback/customers`
  - See controller: `gatewayService/src/main/java/com/saha/amit/gateway/controller/FallbackController.java:1`
- Rate Limiting: RequestRateLimiter filter via RedisRateLimiter (10 req/s, burst 20) by client IP.
  - Requires Redis. In Docker Compose, `redis` is provided.
  - For local runs, set `SPRING_DATA_REDIS_HOST=localhost` (default) or to your Redis host.
- Path Rewrite: Additional routes accept `/api/orders/**` and `/api/customers/**` and rewrite to backend `/orders/**` and `/customers/**`.

Examples
- Call orders through gateway: `curl -i http://localhost:8085/orders`
- Path rewrite: `curl -i http://localhost:8085/api/orders`
- Observe gateway headers: `curl -i http://localhost:8085/orders | grep -i x-gateway`
- Simulate fallback (stop the target service) then call: `curl -i http://localhost:8085/orders` → returns 503 with JSON from fallback.

### Discovery Service
- Path: `discoveryService`
- Run: `cd discoveryService && ./mvnw spring-boot:run`
- Dashboard: `http://localhost:8761`

### Config Service
- Path: `configService`
- Run: `cd configService && ./mvnw spring-boot:run`
- Mode: `native` (reads configs from `configService/src/main/resources/config`)
- Central configs for: `order-service`, `customer-service`, `gateway-service`
- Spring Cloud Bus (Kafka) enabled for broadcasting refresh events

Config files:
- Order: `configService/src/main/resources/config/order-service.properties`
- Customer: `configService/src/main/resources/config/customer-service.properties`
- Gateway: `configService/src/main/resources/config/gateway-service.yml`

## Kafka (Order Service)
- Outbox pattern persists events and a background publisher sends to Kafka.
- Retry and DLQ topics configured; see `application.properties` and `KafkaConfig` for details.
- Protobuf + Schema Registry example included.

## Running Locally
1. Start MySQL (or update R2DBC URL to your DB).
2. Start the discovery server: `cd discoveryService && ./mvnw spring-boot:run`
3. Start the config server: `cd configService && ./mvnw spring-boot:run`
4. Start the order service: `cd reactiveOrderService && ./mvnw spring-boot:run`
5. Start the customer service: `cd customerService && ./mvnw spring-boot:run`
6. Start the API Gateway: `cd gatewayService && ./mvnw spring-boot:run` (listens on `8085`)
7. Start the Angular web app:
   - `cd webapp`
   - `npm install`
   - `npm start` (opens http://localhost:4200)
   - Angular dev server proxies `/api` -> `http://localhost:8085`, so the UI calls the gateway automatically.

Swagger UIs:
- Order: `http://localhost:8080/swagger-ui/index.html`
- Customer: `http://localhost:8081/swagger-ui/index.html`

Gateway routes (via discovery):
- `http://localhost:8085/orders/**` -> order service
- `http://localhost:8085/customers/**` -> customer service

## Angular Test App
- Path: `webapp`
- Purpose: simple UI to exercise customers and orders APIs via the gateway.
- Proxy: `webapp/proxy.conf.json` forwards `/api` to `http://localhost:8085`.
- Usage:
  - Customers tab: create/list customers, click a row to fetch orders for that customer.
  - Orders tab: create/list orders; filter by `customerId`.
- Key files:
  - `webapp/src/app/services/api.service.ts`: API client using Angular HttpClient.
  - `webapp/src/app/app.component.ts`: shell with Material toolbar and routing.
  - `webapp/src/app/customers.component.ts`: Material form/table for customers with toasts and validation.
  - `webapp/src/app/orders.component.ts`: Material form/table for orders with toasts and validation.

Build and run with Docker:
- `cd webapp`
- `docker build -t microservice-webapp:dev .`
- `docker run -p 8087:80 microservice-webapp:dev`
- Open `http://localhost:8087`

## Centralized Config + Live Refresh
- All services import remote config: `spring.config.import=optional:configserver:http://localhost:8888`.
- Edit config files under `configService/src/main/resources/config` to change properties centrally.

Refresh options (Actuator):
- Local instance only: `POST http://localhost:<service-port>/actuator/refresh`
- Broadcast to all (Spring Cloud Bus via Kafka): `POST http://localhost:8888/actuator/busrefresh`

Notes:
- The customer `orderWebClient` bean is `@RefreshScope`, so changes to `app.order-service.base-url` and similar apply after refresh without restarting.
- Spring Cloud Bus (Kafka) requires reachable brokers. Bootstrap servers are configured centrally in the config server files for all services.

## Observability
- Tracing
  - Enabled via Micrometer Tracing + OpenTelemetry bridge.
  - Configure endpoint: `management.otlp.tracing.endpoint` (default `http://localhost:4318/v1/traces`).
  - Sampling rate: `management.tracing.sampling.probability` (default `1.0` in dev).
- Metrics
  - Prometheus scrape endpoint: `GET /actuator/prometheus` on each service.
  - Percentiles/SLOs configured for `http.server.requests`.
  - Resilience4j exports circuit breaker, retry, bulkhead metrics via Micrometer.
- Alerts
  - Sample Prometheus alert rules: `observability/prometheus-alerts.yml` (p95 latency, circuit breaker open, gateway 5xx rates).
  - Import into your Prometheus Alertmanager setup and adjust thresholds to your SLOs.
 - Traces in Grafana
   - Tempo is included in Docker Compose; Grafana is pre-wired with a Tempo datasource.
   - Use Grafana Explore → select Tempo → search by service (order-service, customer-service, gateway-service).
   - Optional dashboard: `observability/grafana/dashboards/tracing-overview.json` provides guidance and an exemplars time series.

## Infra via Docker Compose
- Path: `setup/docker-compose.yaml`
- Starts: Zookeeper, 3x Kafka brokers, Schema Registry, OpenTelemetry Collector (OTLP 4317/4318), Tempo (traces) (3200), Jaeger UI (16686), Prometheus (9090), Grafana (3000), and all Spring services (discovery, config, order, customer, gateway).
- Bring up:
  - `cd setup && docker compose up -d`
- Access:
  - Prometheus: `http://localhost:9090`
  - Grafana: `http://localhost:3000` (user `admin` / `admin`)
  - Jaeger UI: `http://localhost:16686`
- Prometheus scrapes services on the host via `host.docker.internal:<port>/actuator/prometheus`.
- Grafana auto-provisions a Prometheus datasource and loads dashboards from:
  - `observability/grafana/dashboards/http-overview.json`
  - `observability/grafana/dashboards/resilience4j.json`
  - `observability/grafana/dashboards/tracing-overview.json`
- Alerts loaded from: `observability/prometheus-alerts.yml` (mounted into Prometheus).

Notes:
- The OpenTelemetry Collector currently logs incoming traces/metrics. To persist or query traces, add a backend (e.g., Jaeger or Tempo) and update `setup/otel-collector/config.yaml` exporters.
  - Already wired to Tempo and Jaeger; you can use Grafana Tempo datasource or Jaeger UI to inspect traces.
  - Services run inside Docker compose as well; Prometheus scrapes both host and container targets.

## Future Enhancements
- Distributed tracing (OpenTelemetry), dashboards, and rate limiting.
- Kafka Streams aggregations and CQRS read models.
