# Circuit breaker and Retry configured in gateway
Mainly look at the CB and retry for the customers route, which is set more properly

```properties
# 1. Expose the specific endpoints (including circuitbreakers)
management.endpoints.web.exposure.include=health,info,metrics,circuitbreakers,circuitbreakerevents

# 2. Enable the circuit breaker health indicator
management.health.circuitbreakers.enabled=true

# 3. CRITICAL: Register the specific breaker with the health system
# Replace 'customersCb' with your actual circuit breaker name if different
resilience4j.circuitbreaker.instances.customersCb.registerHealthIndicator=true

# 4. Optional: Show full details in the health endpoint
management.endpoint.health.show-details=always


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
```
``` java
                .route("customers", r -> r
                        .path("/customers/**")
                        .filters(f -> f
                                .addRequestHeader("X-From-Gateway", "true")  //✅ Mark request as from gateway
                                .addResponseHeader("X-Gateway", "spring-cloud-gateway")  // ✅Mark response as from gateway
                                .circuitBreaker(cb -> cb.setName("customersCb")
                                        .setFallbackUri("forward:/fallback/customers")
                                        .addStatusCode("500")
                                        .addStatusCode("501")
                                        .addStatusCode("503"))
                                .retry(config -> config
                                        .setRetries(2)
                                        .setMethods(HttpMethod.GET) // Safe to retry
                                        .setStatuses(
                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                HttpStatus.BAD_GATEWAY,
                                                HttpStatus.SERVICE_UNAVAILABLE,
                                                HttpStatus.GATEWAY_TIMEOUT)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofMillis(1000), 2, true))
                                // ✅ Apply custom rate limiter
                                .filter(customRequestRateLimiterGatewayFilterFactory
                                        .apply(new RequestRateLimiterGatewayFilterFactory.Config()))
                        )
                        .uri("lb://customer-service"))

```
First thing to note is the order of filters:
1. Circuit Breaker
2. Retry
This is important because:
- Logical Flow: A request first checks if it's within the Rate Limit. Then the Circuit Breaker checks if the service is even healthy enough to try. Finally, the Retry logic handles any immediate errors from the attempt.

- Efficiency: It prevents "Retry Storms." Because the Circuit Breaker is outside the Retry, once the CB flips to OPEN, the Gateway stops attempting retries entirely, preventing you from spamming a service that is already down.
This uses Spring Cloud Gateway’s CircuitBreaker filter, which is backed by Resilience4j (by default).

## Status codes and recommended retry actions

| Status | Meaning                  | Retry Action                                      |
| ------ | ------------------------ | ------------------------------------------------- |
| 500    | "I crashed."             | Retry once (might be a fluke).                    |
| 502    | "I can't reach the pod." | Retry twice (pod is likely coming back).          |
| 503    | "I'm too busy."          | Retry twice with backoff (wait for load to drop). |
| 504    | "I waited too long."     | Retry once (network lag).                         |
| 4xx    | "You sent bad data."     | NEVER retry (it will fail again).                 |



## Status codes and circuit breaker actions

### When does the circuit breaker trigger?

By default, Resilience4j circuit breakers operate on exceptions, not HTTP status codes, by default. The circuit breaker sits after retry and before the request is sent downstream.
1. Retry filter
2. Circuit breaker filter
3. Load balancer (lb://)
4. Downstream service

Circuit breaker does NOT work on HTTP status codes by default. It works on exceptions. Resilience4j marks a call as failed when an exception is thrown, such as:
- ❌ Connection refused (service down)
- ❌ DNS / load-balancer resolution failure
- ❌ Read or connect timeout
- ❌ Runtime exception during downstream call

What does not count as a failure by default
- ✅ HTTP 5xx responses by themselves
- The TCP connection succeeded
- valid HTTP response was received
- exception occurred

We can map certain HTTP status codes to exceptions so that the circuit breaker treats them as failures. Here’s a recommended mapping which we have implemented in the above config:

| Status | Meaning             | Retry Action       | Circuit Breaker (Trip?) | Why?                                                                  |
| ------ | ------------------- | ------------------ | ----------------------- | --------------------------------------------------------------------- |
| 500    | Internal Error      | Retry 1x           | YES                     | Could be a transient DB blip (retry) or a code crash (CB).            |
| 502    | Bad Gateway         | Retry 2x           | YES                     | Downstream pod is likely restarting; retry helps find the new one.    |
| 503    | Service Unavailable | Retry 2x + Backoff | YES                     | The service is overloaded. Retry slowly; trip CB if it persists.      |
| 504    | Gateway Timeout     | Retry 1x           | YES                     | Request took too long. A single retry can bypass a slow network path. |
| 408    | Request Timeout     | Retry 1x           | YES                     | Client-to-Server timeout. Usually a network/infrastructure issue.     |
| 429    | Too Many Requests   | NO                 | NO                      | Handled by your Rate Limiter. Don't trip CB or you'll block everyone. |
| 4xx    | Bad Request / Auth  | NO                 | NO                      | Client errors (400, 401, 404) are permanent; retrying is useless.     |


##  What each part means?
### 1️⃣ setName("ordersCb")
    - This is the logical circuit breaker instance name
    - Resilience4j creates one circuit breaker per name
    - Metrics, state (OPEN / CLOSED / HALF_OPEN), and config are tracked per name


So you effectively have:
- ordersCb → protects order-service
- customersCb → protects customer-service

### 2️⃣ setFallbackUri("forward:/fallback/orders")
When the circuit breaker rejects a call, Gateway does not call the backend. Instead, it internally forwards the request to /fallback/orders


# Circuit Breaker lifecycle
## Normal state: CLOSED
- Requests flow normally to the downstream service.
- Failures are monitored.
- Failures only count after retries are exhausted.
## Failure threshold reached → OPEN
- Once failure rate crosses the configured threshold (default or configured elsewhere):
- Circuit transitions to OPEN
- In OPEN state:
  - No call is made to the downstream service
  - Requests are immediately short-circuited
## Fallback behavior
- `.setFallbackUri("forward:/fallback/orders") ` Gateway internally forwards the request to: `/fallback/orders`
- This is a local forward, not an HTTP redirect.
- No network hop.
- No downstream service invocation
## HALF_OPEN state
- After the wait duration: Circuit allows limited test requests
- Outcomes:
    - Success → circuit moves back to CLOSED
    - Failure → circuit reopens (OPEN)
  

## Flow

```
Client Request
  ↓
Gateway (Global Filters / Rate Limiter)
  ↓
Circuit Breaker (The "Gatekeeper")
  ├─ If OPEN: 🛑 STOP. Go to Fallback immediately.
  └─ If CLOSED/HALF_OPEN: ✅ PASS.
       ↓
       Retry (The "Helper")
         ├─ Try 1
         ├─ (If error) → Wait/Backoff → Try 2
         └─ (If final error) → Return error to Circuit Breaker
              ↓
              Circuit Breaker records failure & updates state
```

## Since you didn’t define custom Resilience4j config, defaults apply:

If the downstream service returns: ```HTTP/1.1 500 Internal Server Error```

Then technically:
- The TCP connection succeeded
- A valid HTTP response was received
- No exception occurred

# Testing

We verify customer service is up using this endpoint
```java
    @GetMapping("/public/success")
    public String dummySuccess() {
        // Any unhandled RuntimeException replicates a 500 Internal Server Error
        log.info("Simulating Successful request");
        return "Health check OK";
    }
```
```bash
curl -i \
http://localhost:8085/customers/public/success \
-H "X-Client-Id: test-client"
```

## ✅Test 1 Retry on HTTP 503

We have and end point in customer-service that simulates a 503 error:
```java
    @GetMapping("/error/503")
    public ResponseEntity<String> simulate503() {
        // Replicates 503 Service Unavailable
        log.info("Simulating 503 SERVICE_UNAVAILABLE");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("The server is currently undergoing maintenance.");
    }
```

Script to run
``` bash
for i in {1..1}; do
  curl -i \
    -X GET http://localhost:8085/customers/error/503 \
    -H "X-Client-Id: test-client"
done
```
1️⃣ Customer-service logs

You should see 3 log entries:
```logs
Simulating 503 SERVICE_UNAVAILABLE
Simulating 503 SERVICE_UNAVAILABLE
Simulating 503 SERVICE_UNAVAILABLE
```

No change in circuit breaker metrics still closed
```
http://localhost:8085/actuator/health 
```
Expected result: 
```"state": "CLOSED"```

Successful call still works
```bash
curl -i \
http://localhost:8085/customers/public/success \
-H "X-Client-Id: test-client"
```

## ✅TEST 2 — Force circuit breaker to OPEN
What is being tested
- Failure rate threshold crossed
- Circuit breaker transitions from CLOSED → OPEN

Confirm success path works
```bash
curl -i http://localhost:8085/customers/public/success \
-H "X-Client-Id: test-client"
```
Circuit breaker state should be CLOSED ```http://localhost:8085/actuator/health```

Run this 4 times
```bash
for i in {1..4}; do
  curl -i http://localhost:8085/customers/error/503 \
  -H "X-Client-Id: test-client"
done
```

Circuit breaker is now OPEN``` http://localhost:8085/actuator/health``` 

## ✅TEST 3 — Verify short-circuiting (fallback)
What is being tested
- Circuit breaker blocks downstream calls
- Fallback is invoked immediately

Run success path again
```bash
curl -i http://localhost:8085/customers/public/success \
-H "X-Client-Id: test-client"
```
You will notice
- Instead of success, you should see the fallback response
- And no logs in customer-service
```json
{"service":"customer-service","status":503,"message":"Customer service is temporarily unavailable"}
```

## ✅TEST 4 — Wait for circuit breaker to HALF_OPEN
Wait: 30 seconds ```waitDurationInOpenState=30s```

Run success path again few times

```bash
curl -i http://localhost:8085/customers/public/success \
-H "X-Client-Id: test-client"
```


It will be in ```HALF_OPEN``` state now ``` http://localhost:8085/actuator/health```

## ✅TEST 5 — HALF_OPEN and recovery
What is being tested is Circuit breaker recovery logic

Send 5 successful requests


```bash
for i in {1..5}; do
  curl -i http://localhost:8085/customers/public/success \
  -H "X-Client-Id: test-client"
done

```
Expected:
- Requests forwarded to customer-service
- No fallback
- Circuit transitions to CLOSED

Circuit breaker is now CLOSED``` http://localhost:8085/actuator/health```

## Circuit Breaker State Mapping (for reference)

| Metric Value | State     |
| ------------ | --------- |
| `0.0`        | CLOSED    |
| `1.0`        | OPEN      |
| `2.0`        | HALF_OPEN |

# Important Notes
- Circuit Breaker works on exceptions by default, not HTTP status codes.
- Retry happens before Circuit Breaker in the filter chain.
- Fallbacks are local forwards within the Gateway, not HTTP redirects.
- We have implemented on entire route (/customers/**) but can be done per endpoint as needed. Not all endpoints need circuit breakers.
- Retry should be done only on idempotent methods (GET, HEAD). Avoid retries on POST/PUT/DELETE unless absolutely necessary.
- Retry with backoff helps prevent overwhelming a recovering service.
- Retry should be done conservatively to avoid cascading failures.