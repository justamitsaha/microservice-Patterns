# Distributed Tracing Setup Guide
## 1. Add tracing dependencies (Gateway + all services)
```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

## 2. Configure tracing properties (Gateway + all services)
```properties
management.tracing.enabled=true
management.tracing.sampling.probability=1.0
```
## 3. Update logback-spring.xml:
```xml
<pattern>
  %d{HH:mm:ss.SSS} [%thread]
  traceId=%X{traceId:-} spanId=%X{spanId:-}
  %-5level %logger{36} - %msg%n
</pattern>
```

## 4. In Spring Boot 3.2+, Reactor does NOT propagate context automatically unless you enable it.

Add this to every reactive service (gateway, customer, order):
```properties
spring.reactor.context-propagation=auto
```