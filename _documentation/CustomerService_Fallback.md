# Customer Service Fallback and Resilience Documentation

This document captures the multi-layer fallback behavior implemented for the `customerService`, both at the API Gateway level and within the service itself.

## 1. Gateway-Level Fallback (Edge Protection)

The API Gateway (`gatewayService`) protects the `customerService` using a dedicated circuit breaker named `customersCb`.

### Configuration
*   **Trigger:** The circuit breaker trips if the `customerService` is down, slow, or returns specific 5xx errors (500, 501, 503).
*   **Fallback URI:** `forward:/fallback/customers`

### Handling POST/PUT/DELETE Methods (The 405 Fix)
By default, Spring Cloud Gateway preserves the original HTTP method during a forward. If a user sends a `POST` request to register a customer and the service is down, the Gateway forwards a `POST` to the fallback URI.

**Implementation Note:**
The `FallbackController` in the Gateway is configured using `@RequestMapping` (instead of `@GetMapping`) to ensure it can handle any HTTP method. This is done because say a `POST` request is failing or is having delay. In this case it will be forwarded to the fallback, if the fallback only has `@GetMapping`, it will not be able to handle the `POST` request and will return a `405 Method Not Allowed` error instead of the intended fallback response. This will confuse clients and mask the actual issue (service unavailability) with a misleading HTTP error.
```java
@RequestMapping("/fallback/customers")
public ResponseEntity<Map<String, Object>> customersFallback() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                    "service", "customer-service",
                    "message", "Customer service is temporarily unavailable",
                    "status", 503
            ));
}
```

---

## 2. Service-Level Fallback (Internal Resilience)

The `customerService` makes calls to the `order-service` to aggregate customer data with their orders. This call is wrapped in a resilience pipeline using **Resilience4j**.

### Pipeline Architecture
The `WebClient` call in `CustomerService.java` follows this execution order:
1.  **Retry:** Handles transient network issues.
2.  **Circuit Breaker:** Prevents cascading failures if `order-service` is unstable.
3.  **Bulkhead:** Limits concurrent calls to protect the `customerService` resources.
4.  **Fallback:** Executed if the pipeline fails.

### Fallback Logic (`fallbackOrders`)
If the call to `order-service` fails (e.g., circuit is OPEN or request timed out), the system gracefully degrades by returning a placeholder response instead of failing the entire customer request.

```java
private Flux<OrderResponse> fallbackOrders(Throwable ex) {
    if (ex instanceof TimeoutException) {
        // Return a specific placeholder for timeouts
        return Flux.just(new OrderResponse("N/A", null, 0.0, "TIMEOUT_FALLBACK"));
    }
    // Return a general unavailable placeholder
    return Flux.just(new OrderResponse("N/A", null, 0.0, "SERVICE_UNAVAILABLE"));
}
```

### Resulting Behavior
When the `order-service` is down, a call to `GET /customers/{id}` will still return the customer's basic details (from the DB), but the `orders` list will contain a placeholder item indicating that the order service was unavailable.

---

## 3. Testing Fallbacks

### Testing Gateway Fallback
1.  Stop the `customerService`.
2.  Attempt to register a customer:
    ```bash
    curl -X POST http://localhost:8085/customers \
         -H "Content-Type: application/json" \
         -d '{"name":"Test","email":"test@test.com","password":"..."}'
    ```
3.  **Expected:** A 503 JSON response from the Gateway fallback (NOT a 405 error).

### Testing Service Fallback
1.  Ensure `customerService` and the Gateway are running.
2.  Stop the `order-service`.
3.  Call the customer detail endpoint: `GET http://localhost:8085/customers/{id}`.
4.  **Expected:** The request succeeds with HTTP 200, but the orders section shows `SERVICE_UNAVAILABLE`.
