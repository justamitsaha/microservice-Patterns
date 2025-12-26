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
``` properties
logging.pattern.console=${LOG_PATTERN_CONSOLE:%green(%d{HH:mm:ss.SSS}) %blue(%-5level) %red([%thread]) clientId=%X{clientId:-} traceId=%X{traceId:-} spanId=%X{spanId:-} %yellow(%class.%M:%L) - %msg%n}
```

## 4. In Spring Boot 3.2+, Reactor does NOT propagate context automatically unless you enable it.

Add this to every reactive service (gateway, customer, order):
```properties
spring.reactor.context-propagation=auto
```
With this enabled, Reactor will automatically propagate the tracing context across reactive operators. And we will be able to see traceId and spanId in the logs. But if we want to add custom fields (like clientId) to the logs, we need to manually set them in the Reactor context.
In WebFlux, you need to propagate context through the reactive pipeline, not just ThreadLocal variables.

Key changes:

- Inject Tracer and BaggageManager - These are needed to properly manage baggage in the current trace context
- Use ```BaggageManager.createBaggage()``` - This properly creates and registers the baggage with the current span
- Use ```Mono.deferContextual()``` - This ensures the baggage is set within the reactive context
## 5. Manually propagate custom fields (like clientId) in reactive services

```java
@Component
public class ClientIdBaggageFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(ClientIdBaggageFilter.class);
    private final Tracer tracer;
    private final BaggageManager baggageManager;

    public ClientIdBaggageFilter(Tracer tracer, BaggageManager baggageManager) {
        this.tracer = tracer;
        this.baggageManager = baggageManager;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String clientId = exchange.getRequest()
                .getHeaders()
                .getFirst("X-Client-Id");

        logger.debug("➡️ Gateway received request {} with X-Client-Id={}", path, clientId);

        if (clientId == null) {
            return chain.filter(exchange);
        }

        // Add clientId to baggage in the current span
        return Mono.deferContextual(ctx -> {
            // Get current span and add baggage
            if (tracer.currentSpan() != null) {
                Baggage baggage = baggageManager.createBaggage("clientId", clientId);
                baggage.makeCurrent();
                logger.debug("✅ Set clientId={} in baggage", clientId);
            }
            return chain.filter(exchange);
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
```

Now we can see client ID along with traceId and spanId in the logs across all services.
```logs
13:02:24.326 INFO  [lettuce-nioEventLoop-5-1] clientId=5c60b6d6-cafa-4792-8dd8-515d1894c0db traceId=60336efd3fa0e37b118bf83d5f8dcd3c spanId=2fd40b2c6e46a517 com.saha.amit.gateway.config.GatewayRoutesConfig.lambda$logClientIdFilter$1:54 - ➡️ Gateway received request /orders with X-Client-Id=5c60b6d6-cafa-4792-8dd8-515d1894c0db
13:02:25.335 INFO  [parallel-6] clientId=5c60b6d6-cafa-4792-8dd8-515d1894c0db traceId=60336efd3fa0e37b118bf83d5f8dcd3c spanId=2fd40b2c6e46a517 com.saha.amit.gateway.config.GatewayRoutesConfig.lambda$clientIdKeyResolver$0:32 - 🔑 KeyResolver resolved key = 5c60b6d6-cafa-4792-8dd8-515d1894c0db
13:02:25.422 ERROR [lettuce-nioEventLoop-5-1] clientId=5c60b6d6-cafa-4792-8dd8-515d1894c0db traceId=60336efd3fa0e37b118bf83d5f8dcd3c spanId=82c7760522dd7d08 org.springframework.web.server.adapter.HttpWebHandlerAdapter.handleUnresolvedError:356 - [a7ecb3d8-10] Error [java.lang.UnsupportedOperationException] for HTTP GET "/orders", but ServerHttpResponse already committed (503 SERVICE_UNAVAILABLE)
```

```logs
13:01:50.754 INFO  [reactor-tcp-nio-7] clientId=5c60b6d6-cafa-4792-8dd8-515d1894c0db traceId=638d070156b1a62e1df598a0092cd213 spanId=452be94d00fe7f4e com.saha.amit.customer.controller.CustomerController.lambda$login$1:87 - Access token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiZW1haWwiOiIxN2FzYWhhQGdtYWlsLmNvbSIsImlhdCI6MTc2NjczNDMxMCwiZXhwIjoxNzY2NzM1MjEwfQ.P1wLj14XM2nbphBogeyDrADmwHk-um8aX56LdBjm2F8
13:01:50.773 INFO  [reactor-tcp-nio-7] clientId=5c60b6d6-cafa-4792-8dd8-515d1894c0db traceId=638d070156b1a62e1df598a0092cd213 spanId=452be94d00fe7f4e com.saha.amit.customer.controller.CustomerController.lambda$login$1:91 - ✅ Access token issued for userId=1 email=17asaha@gmail.com issuedAt=Fri Dec 26 13:01:50 IST 2025 expiresAt=Fri Dec 26 13:16:50 IST 2025
13:01:50.779 INFO  [reactor-tcp-nio-7] clientId=5c60b6d6-cafa-4792-8dd8-515d1894c0db traceId=638d070156b1a62e1df598a0092cd213 spanId=452be94d00fe7f4e com.saha.amit.customer.controller.CustomerController.lambda$login$1:108 - 💾 Refresh token cookie set for userId=1 (HttpOnly, 7 days validity)
13:01:50.799 INFO  [reactor-tcp-nio-7] clientId=5c60b6d6-cafa-4792-8dd8-515d1894c0db traceId=638d070156b1a62e1df598a0092cd213 spanId=452be94d00fe7f4e com.saha.amit.customer.controller.CustomerController.lambda$login$3:125 - 🔒 Login process completed for email: 17asaha@gmail.com
```

```logs
12:59:40.292 INFO  [AsyncResolver-bootstrap-executor-%d] clientId= traceId= spanId= com.netflix.discovery.shared.resolver.aws.ConfigClusterResolver.getClusterEndpoints:43 - Resolving eureka endpoints via configuration
13:02:25.303 INFO  [reactor-http-nio-2] clientId=5c60b6d6-cafa-4792-8dd8-515d1894c0db traceId=60336efd3fa0e37b118bf83d5f8dcd3c spanId=27328d57ae300710 com.saha.amit.reactiveOrderService.controller.OrderController.getOrders:56 - Received order retrieval request for customerId: null
13:02:25.309 INFO  [reactor-http-nio-2] clientId=5c60b6d6-cafa-4792-8dd8-515d1894c0db traceId=60336efd3fa0e37b118bf83d5f8dcd3c spanId=27328d57ae300710 com.saha.amit.reactiveOrderService.service.OrderService.getOrdersByCustomer:39 - Inside getOrdersByCustomer for customerId: null
```


# Spring Boot Distributed Tracing - How It Works

## 🔍 How Trace Propagation Works

### 1. **HTTP Header Propagation**
When a request crosses service boundaries, Spring Boot automatically injects trace context into HTTP headers:

**Outgoing Request Headers:**
```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
baggage: clientId=user-123
```

**Format Breakdown:**
- `traceparent` (W3C standard): `version-traceId-spanId-flags`
- `baggage`: Key-value pairs for custom fields

### 2. **Key Components Involved**

```
┌─────────────┐     HTTP Request      ┌─────────────┐
│  Gateway    │ ───────────────────> │  Service A  │
│             │  + traceparent header │             │
│ TraceId: X  │  + baggage: clientId  │ TraceId: X  │
│ SpanId: 1   │                       │ SpanId: 2   │
└─────────────┘                       └─────────────┘
      │                                      │
      │ Creates new span                    │ Extracts context
      │ Injects headers                     │ from headers
      ↓                                      ↓
  Micrometer Tracing              Micrometer Tracing
  + Baggage Manager               + Baggage Manager
```

### 3. **Auto-Configuration Magic**

Spring Boot auto-configures these components:

#### **For WebFlux (Reactive)**
- `WebClientExchangeTagsProvider` - Adds trace headers to WebClient
- `ObservationWebClientCustomizer` - Wraps WebClient with tracing
- `ServerHttpObservationFilter` - Extracts trace headers from incoming requests

#### **For Servlet (Traditional)**
- `RestTemplateInterceptor` - Adds trace headers to RestTemplate
- `TracingFilter` - Extracts trace headers from incoming requests

---

## ⚙️ Configuration Knobs

### **1. Baggage Configuration**

```yaml
management:
  tracing:
    baggage:
      # Fields to propagate via HTTP headers
      remote-fields: 
        - clientId
        - userId
        - sessionId
      
      # Fields to add to MDC (logs)
      correlation:
        enabled: true
        fields:
          - clientId
          - userId
      
      # Custom baggage configuration
      tag-fields:  # Add baggage as span tags
        - clientId
```

**What each does:**
- `remote-fields`: Baggage keys sent in `baggage` HTTP header
- `correlation.fields`: Baggage keys added to MDC for logging
- `tag-fields`: Baggage keys added as span attributes (visible in Zipkin/Jaeger)

---

### **2. Sampling Configuration**

```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # 0.0 to 1.0 (0% to 100%)
      # 1.0 = trace everything (dev/testing)
      # 0.1 = trace 10% (production)
```

**Custom Sampler:**
```java
@Bean
public Sampler customSampler() {
    return new Sampler() {
        @Override
        public SamplingResult shouldSample(
            Context parentContext,
            String traceId,
            String name,
            SpanKind spanKind,
            Attributes attributes,
            List<LinkData> parentLinks
        ) {
            // Sample based on custom logic
            if (name.startsWith("/api/critical")) {
                return SamplingResult.recordAndSample();
            }
            return SamplingResult.drop();
        }
    };
}
```

---

### **3. Propagation Format**

```yaml
management:
  tracing:
    propagation:
      type: W3C  # Options: W3C, B3, B3_MULTI
      
      # Or configure multiple
      produce: W3C,B3
      consume: W3C,B3,B3_MULTI
```

**Formats:**
- `W3C` (default): Modern standard, single `traceparent` header
- `B3`: Zipkin format, multiple headers (`X-B3-TraceId`, `X-B3-SpanId`)
- `B3_MULTI`: Legacy Zipkin format

---

### **4. Exporter Configuration**

```yaml
management:
  tracing:
    export:
      # Send to Zipkin
      zipkin:
        enabled: true
        endpoint: http://localhost:9411/api/v2/spans
      
      # Or OTLP (OpenTelemetry)
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
      headers:
        authorization: Bearer ${OTLP_TOKEN}
```

---

### **5. Reactive Context Propagation**

```yaml
spring:
  reactor:
    context-propagation: auto  # Critical for WebFlux!
```

**What it does:**
- Automatically propagates trace context through reactive chains
- Without this, trace context is lost between operators

---

### **6. Span Customization**

```yaml
management:
  observations:
    http:
      server:
        requests:
          name: http.server.requests
      client:
        requests:
          name: http.client.requests
  
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${ENV:dev}
```

**Programmatic Customization:**
```java
@Component
public class CustomObservationHandler 
    implements ObservationHandler<Observation.Context> {
    
    @Override
    public void onStart(Observation.Context context) {
        // Add custom tags to spans
        if (context instanceof ServerRequestObservationContext ctx