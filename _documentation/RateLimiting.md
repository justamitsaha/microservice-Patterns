# Robust Multi-Tier Rate Limiting Documentation

This document explains the implementation of the secure, multi-tier rate limiting system designed for the Microservice Patterns Playground.

## Overview
To protect public and private APIs from abuse while maintaining a smooth user experience, we implemented a strategy that uses a secure handshake to identify anonymous clients and applies rate limits at multiple levels (Token-based and IP-based) using Redis as a centralized store.

### The Handshake Logic
1.  **Handshake (`/init`):** The client calls the `/init` endpoint on the Gateway.
2.  **Token Generation:** The Gateway generates a unique, secure `client_token` (UUID).
3.  **Metadata Storage:** The Gateway extracts the client's **IP Address** and **User-Agent** and stores this metadata in Redis, indexed by the `client_token`.
4.  **HttpOnly Cookie:** The Gateway returns the `client_token` in an `HttpOnly`, `SameSite=Strict` cookie named `client_token`.
5.  **Session Tracking:** Subsequent requests from the browser automatically include this cookie.

---

## Technical Background: RedisRateLimiter
Spring Cloud Gateway provides distributed, per-client rate limiting using Redis. It implements the **Token Bucket Algorithm**:
- **Replenish Rate**: How many tokens are added to the bucket per second (sustained throughput).
- **Burst Capacity**: Maximum tokens the bucket can hold (allows short spikes in traffic).
- **Requested Tokens**: How many tokens each request consumes (default is 1).

**Benefits:**
- **Shared State:** Limits are consistent across all Gateway instances.
- **Atomicity:** Uses Lua scripts in Redis to ensure thread-safe incrementing/decrementing.
- **Horizontal Scaling:** Safe for high-availability deployments.

---

## Implementation Details

### 1. API Gateway (`gatewayService`)

#### `InitGatewayFilter.java`
Handles the `/init` handshake. It generates the `client_token`, stores `{ip, ua}` metadata in Redis for 24 hours, and issues the `HttpOnly` cookie.

#### `GatewayRoutesConfig.java`
Defines the tiered protection logic:
*   **Tier 1: Cookie-based (`cookieKeyResolver`)** - Uses the `client_token` cookie. Limit: 5 req/s.
*   **Tier 2: IP-based (`ipKeyResolver`)** - Fallback protection against cookie clearing. Limit: 2 req/s.
*   **Backward Compatibility:** Still supports `X-Client-Id` header via `clientIdKeyResolver`.

#### `SecurityConfig.java`
CORS is configured to `allowCredentials(true)` to permit the browser to send/receive the `client_token` cookie.

### 2. Frontend (`webapp`)
*   **`api.service.ts`**: The `init()` method bootstraps the session.
*   **`HttpOptions`**: All API calls use `{ withCredentials: true }` to ensure the `client_token` cookie is attached by the browser.

---

## How to Test

### Step 1: Perform the Handshake
```bash
# Call /init and save the cookie to 'cookies.txt'
curl -v -X POST http://localhost:8085/init -c cookies.txt
```

### Step 2: Access a Public API
```bash
curl -v -b cookies.txt http://localhost:8085/customers/public/success
```

### Step 3: Simulate Token-Based Abuse (5 req/s)
```bash
# Rapidly fire requests using the same cookie
for i in {1..10}; do
  curl -s -o /dev/null -w "%{http_code}\n" -b cookies.txt http://localhost:8085/customers/public/success
done
```
*Expected:* You will see `200` followed by `429` once the bucket is empty.

### Step 4: Simulate IP-Based Abuse (2 req/s)
```bash
# Rapidly fire requests WITHOUT a cookie
for i in {1..10}; do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8085/customers/public/success
done
```
*Expected:* You will hit `429` much faster due to the stricter IP limit.

### Step 5: Legacy Header Test (`X-Client-Id`)
```bash
curl -i -H "X-Client-Id: manual-test-id" http://localhost:8085/customers/public/success
```

### Step 6: Verify Redis Metadata
```bash
redis-cli KEYS "client_metadata:*"
```
