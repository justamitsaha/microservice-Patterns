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


1. Step 1: User calls `/init`. 
Gateway executes: `String clientToken = UUID.randomUUID().toString(); `. This generates a unique identifier for the client session. Example: `f4a8f5d2-c8d2-4d2d-9b1c-123456789abc`. This is stored in Redis: ` client_metadata:f4a8f5d2-c8d2-4d2d-9b1c-123456789abc` This is sent back to the client as an HttpOnly cookie: Response cookie: `Set-Cookie:client_token=f4a8f5d2-c8d2-4d2d-9b1c-123456789abc`

2. Step 2: Request hits /customers/public/** For this route we have two layers of rate limiting which intercepts the request:
    ```java
                    // Public Customers route (Tiered Rate Limiting: Token then IP)
                    .route("public-customers", r -> r
                            .path("/customers/public/**")
                            .filters(f -> f
                                    .addRequestHeader("X-From-Gateway", "true")
                                    .addResponseHeader("X-Gateway", "spring-cloud-gateway")
                                    // Tier 1: Per Token
                                    .requestRateLimiter(c -> c.setRateLimiter(tokenRateLimiter).setKeyResolver(cookieKeyResolver))
                                    // Tier 2: Per IP (as fallback/secondary protection)
                                    .requestRateLimiter(c -> c.setRateLimiter(ipRateLimiter).setKeyResolver(ipKeyResolver))
                            )
                            .uri("lb://customer-service"))
    ```
3. The key resolver extracts the `client_token` from the cookie using the `cookieKeyResolver` and returns the `client_token` value as the key for rate limiting. For example, if the generated token like this: `4a8f5d2-c8d2-4d2d-9b1c-123456789abc`, then the `cookieKeyResolver` will extract this token from the cookie and use it as the key for the RedisRateLimiter. The RedisRateLimiter will then track the number of requests associated with this token and apply the defined rate limits accordingly.

    ```java
        // 1️⃣ Key resolver based on 'client_token' cookie (Anonymous session identification)
    @Bean
    @Primary
    public KeyResolver cookieKeyResolver() {
        return exchange -> {
            HttpCookie cookie = exchange.getRequest().getCookies().getFirst("client_token");
            String token = (cookie != null) ? cookie.getValue() : null;
            logger.debug("🔑 cookieKeyResolver resolved token = {}", token);
            return Mono.justOrEmpty(token);
        };
    }
    
    // 2️⃣ Key resolver based on Remote IP Address (Fallback for cookie-less or rotated requests)
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getRemoteAddress() != null ?
                    exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
            logger.debug("🔑 ipKeyResolver resolved IP = {}", ip);
            return Mono.just(ip);
        };
    }
    ```

4. Then redis rate limiter comes into play. The `tokenRateLimiter` allows 5 requests per second for each unique `client_token`, while the `ipRateLimiter` allows only 2 requests per second for each IP address. This means that if a client exceeds the token-based limit, they will receive a `429 Too Many Requests` response. 
    ```java
        // 3️⃣ Redis rate limiters
        @Bean
        @Primary
        public RedisRateLimiter tokenRateLimiter() {
            return new RedisRateLimiter(5, 10); // Token limit: 5 req/s
        }
    
        @Bean
        public RedisRateLimiter ipRateLimiter() {
            return new RedisRateLimiter(2, 5); // Strict IP limit: 2 req/s
        }
    ```
    Examples:
    
    | Requests | Result |
    | --- | --- |
    | 5/sec | Allowed |
    | 8/sec | Allowed for short burst |
    | 10 immediately | Allowed |
    | 11th immediately | Rejected (429) |

5. The IP-based limiter serves as a secondary defense mechanism, ensuring that even if a client tries to bypass the token limit (e.g., by clearing cookies), they will still be subject to rate limiting based on their IP address.The IP-based limit exists to prevent someone from bypassing the token-based limit. Without IP limiting Attacker does:

    ```
    GET /init  -> Token A
    GET /init  -> Token B
    GET /init  -> Token C
    GET /init  -> Token D
    ```

    Now they have 4 valid tokens. Each token gets: `5 req/sec` . So total:

    ```
    Token A = 5 req/sec
    Token B = 5 req/sec
    Token C = 5 req/sec
    Token D = 5 req/sec
    -----------------------
    Total    = 20 req/sec
    ```
    The attacker simply requests more tokens and bypasses your intended limit. With IP limiting Suppose the same attacker comes from `192.168.1.10` and gets 100 tokens. Even then: `new RedisRateLimiter(2, 5)` using `ipKeyResolver()` creates a bucket for: `192.168.1.10` All requests from that IP share the same bucket.
    ```
    Token A ─┐
    Token B ─┼──> IP Bucket
    Token C ─┤
    Token D ─┘
    ```
    Result: 
    ```
    Per Token Limit = 5 req/sec
    Per IP Limit    = 2 req/sec
    ```
    The IP limit becomes the final protection.

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

1. Get container name `docker ps`
2. Access Redis CLI inside the container: `docker exec -it <redis-container-name> redis-cli` e.g.  `docker exec -it redis redis-cli`
3. List all client metadata keys `KEYS *` or more specifically: `keys client_metadata:*`
4. Get metadata for a specific token: `GET client_metadata:f4a8f5d2-c8d2-4d2d-9b1c-123456789abc`