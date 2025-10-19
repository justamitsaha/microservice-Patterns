# Objectives -Learning Microservice Patterns
I I want to learn microservice architectures, various design patterns. For this, I have chosen this use case where I will create two Spring Boot applications and other supporting services needed as part of microservice family 
1. One customer service
2. Reactive order service. The reactive order service is already created and kept in this folder.
3. API Gateway to handel all the traffic
4. Discovery Service
5. Configuration Service, which will hold all the configurations.

# Use case
## Order Service 
1. Add GET orders endpoints to existing API

## Customer Service 
1. create CRUD end points GET/POST/PUT/DELETE
2. GET customer should call GET orders API and show orders in response

## Tech Stack
- Java 21, Spring Boot 3 (WebFlux, Actuator).
- Project Reactor
- R2DBC (MySQL driver) for reactive persistence.
- Micrometer + Prometheus registry, OpenTelemetry OTLP exporter.
- API Gateway Pattern — single entry point for all clients.
- Circuit Breaker Pattern — prevent cascading failures.
- Distributed Tracing Pattern — trace requests across services.
- Centralized Configuration Pattern — externalize configuration.
- Retry Pattern — handle transient failures gracefully.
- Rate Limiting Pattern — control traffic and prevent abuse.
- Swagger for documentation




