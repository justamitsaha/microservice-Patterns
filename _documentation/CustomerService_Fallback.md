# Customer Service Resiliency, Fallback, and Timeout Documentation

This document provides a comprehensive overview of the resilience patterns, timeout configurations, and fallback strategies implemented for the `customerService`, spanning both the API Gateway and the service itself.

---

## 1. Edge Layer: API Gateway (`gatewayService`)

The Gateway serves as the first line of defense, protecting the system from cascading failures using **Spring Cloud Circuit Breaker** (backed by Resilience4j).

### A. Circuit Breaker Configuration (`customersCb`)
The circuit breaker monitors requests to the `/customers/**` routes. 
*Note: The values below are applied via the Gateway's local properties as a default, but are overridden by the Config Server for the `orderService` internal call.*

**Gateway-to-Customer (`customersCb`):**
*   **Sliding Window:** 20 calls.
*   **Failure Threshold:** 50%.
*   **Wait Duration:** 30 seconds.

**Customer-to-Order (`orderService`):**
*As defined in `temp/configurationServer/customer-service.properties`:*
*   **Sliding Window Size:** 6 calls (COUNT_BASED).
*   **Minimum Number of Calls:** 3.
*   **Failure Threshold:** 50%.
*   **Wait Duration:** 10 seconds.
*   **Half-Open Calls:** 2.

```properties
# --- Circuit Breaker: customersCb ---
# Calculation starts after 10 calls (prevents early tripping)
resilience4j.circuitbreaker.instances.customersCb.minimumNumberOfCalls=10
# How many calls to remember for the failure rate
resilience4j.circuitbreaker.instances.customersCb.slidingWindowSize=20
# If 50% of the last 20 calls fail, OPEN the circuit
resilience4j.circuitbreaker.instances.customersCb.failureRateThreshold=50
# Give the service 30 seconds to recover before trying again
resilience4j.circuitbreaker.instances.customersCb.waitDurationInOpenState=30s
# In HALF_OPEN state, allow 5 "probe" calls to see if it's fixed
resilience4j.circuitbreaker.instances.customersCb.permittedNumberOfCallsInHalfOpenState=5

# Increase timeout to 3s to allow for slow operations like password hashing during registeration
resilience4j.timelimiter.instances.customersCb.timeoutDuration=3s
```

### B. Time Limiter (Global Timeout)
The Gateway and the Service both enforce timeouts.
*   **Gateway (customersCb):** The default value is **1s**. 
    *   **The Issue:** The 1s default was causing critical failures during registration. Because password hashing (65k iterations) is CPU-intensive, it often exceeded 1s. This caused the Gateway to prematurely time out, cancel the backend request (triggering `Customer creation cancelled` logs), and return a 405 error (due to method preservation on the fallback).
    *   **The Fix:** We have increased this to **3s** in `application.properties` to ensure registration has enough headroom to complete.
*   **Service (orderService):** Configured as 2s in both property file (`resilience4j.timelimiter.instances.orderService.timeoutDuration=2s`) and Reactor code (`.timeout(Duration.ofSeconds(2))`).

### C. The "405 Method Not Allowed" Fix
When a request fails (or times out), the Gateway forwards it to a local fallback endpoint (`/fallback/customers`).
*   **Challenge:** Spring Cloud Gateway preserves the original HTTP method during the forward. A `POST /customers` becomes a `POST /fallback/customers`.
*   **Solution:** The `FallbackController` uses `@RequestMapping` instead of `@GetMapping` to ensure it can handle `POST`, `PUT`, and `DELETE` requests without returning a 405 error.

---

## 2. Service Layer: Internal Resilience (`customerService`)

The `customerService` aggregates data from the `order-service`. This downstream call is protected by a multi-tier resilience pipeline.

### A. The Resilience Pipeline Architecture
The call to `order-service` via `WebClient` follows this strict order:
1.  **Retry:** Attempt the call up to 2 times (for GET requests) on transient 5xx errors.
2.  **Circuit Breaker:** If the failure rate is too high, stop calling the order service entirely to allow it to recover.
3.  **Bulkhead:** Limit the number of concurrent calls to the order service to prevent thread/resource exhaustion in the customer service.
4.  **Timeout:** Enforced at the Reactor level (`.timeout(Duration.ofSeconds(2))`).

### B. Graceful Degradation (Fallback)
If any stage of the pipeline fails, the `.onErrorResume(this::fallbackOrders)` method is triggered.
*   **Logic:** Instead of failing the entire customer request, the service returns a placeholder "dummy" order with a status like `TIMEOUT_FALLBACK` or `SERVICE_UNAVAILABLE`.
*   **User Experience:** The user still sees customer details, even if their order history is temporarily missing.

---

## 3. Pattern: Handling Blocking/CPU-Intensive Tasks

Reactive programming (Reactor) requires that the "Event Loop" threads never be blocked.

### The Challenge
Operations like **Password Hashing** (PBKDF2 with 65,536 iterations) are CPU-intensive and "block" the thread they run on. If run on the event loop, they freeze the service for all other users.

### The Pattern
We offload these operations to a separate thread pool using the **`boundedElastic`** scheduler:
```java
return Mono.fromCallable(() -> {
    // Perform CPU-heavy hashing here
    return CustomerServiceUtil.hashPassword(password, salt);
})
.subscribeOn(Schedulers.boundedElastic()) // Offload to worker thread
.flatMap(repository::save);
```
*Note: This ensures the high-performance event loop remains free to handle I/O, while workers handle the heavy lifting.*

---

## 4. Testing & Verification

### Scenario 1: Gateway Timeout
1.  Increase hashing iterations or introduce a `Thread.sleep()` in the backend.
2.  Call registration from the browser.
3.  **Result:** Gateway returns the 503 JSON fallback message after **3 seconds** (the configured timeout).

### Scenario 2: Service-Level Fallback
1.  Ensure the Gateway and `customerService` are running.
2.  Stop the `order-service`.
3.  Call `GET /customers/{id}`.
4.  **Result:** Response is `200 OK`. Customer data is present. The `orders` list contains a placeholder item with `SERVICE_UNAVAILABLE`.

### Scenario 3: Method Preservation (405 Prevention)
1.  Stop `customerService`.
2.  Run `curl -X POST http://localhost:8085/customers`.
3.  **Result:** Receives a proper 503 JSON body instead of a "405 Method Not Allowed" error.
