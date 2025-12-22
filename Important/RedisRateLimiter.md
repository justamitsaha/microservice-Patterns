1. Step 1 Browser --> Post init call
2. Step 2 Gateway --> Return  { clientId = UUID }
3. Step 3 Browser --> Saves clientId in Session storage
4. Step 4 Browser --> Subsequent calls with clientId in Header → Angular Interceptor adds X-Client-Id header
5. Step 5 Gateway --> KeyResolver reads X-Client-Id → Rate Limiter checks Redis bucket for that clientId
6. Step 6 Gateway --> If limit exceeded return 429 status code

```bash
    curl -i \
      -X POST http://localhost:8085/customers/login \
      -H "Content-Type: application/json" \
      -H "X-Client-Id: a81276c1-dc80-4682-bcb4-c912701bb43f" \
      -d '{"email":"17asaha@gmail.com","password":"12345"}'
```

```bash
    for i in {1..5}; do
      curl -s -o /dev/null -w "%{http_code}\n" \
        -X POST http://localhost:8085/customers/login \
        -H "Content-Type: application/json" \
        -H "X-Client-Id: a81276c1-dc80-4682-bcb4-c912701bb43f" \
        -d '{"email":"17asaha@gmail.com","password":"12345"}'
    done
```


# H1 **`RedisRateLimiter`** in Spring Cloud Gateway provides distributed, per-client rate limiting using Redis as a centralized store. 
It implements a token bucket algorithm, ensuring consistent limits across multiple gateway instances.
Each incoming request is assigned a key (for example, a client UUID or user ID) via a KeyResolver. For that key, Redis maintains a bucket of tokens:
  - **replenishRate**: number of tokens added to the bucket per second
  - **burstCapacity**: maximum number of tokens the bucket can hold
  - **requestedTokens** (default = 1): tokens consumed per request

On every request:
    - If a token is available → the request is forwarded
    - If no token is available → the gateway immediately returns HTTP 429 (Too Many Requests)

Because Redis is used, rate limits are:
    - Shared across all gateway instances
    - Consistent and atomic (implemented via a Redis Lua script)
    - Safe for horizontal scaling

RedisRateLimiter is typically applied per route and combined with client-specific keys (UUID, JWT subject, API key) to protect backend services while allowing short bursts of traffic without degrading user experience.

Rate limiting in Spring Cloud Gateway is opt-in per route, unless you configure it as a global filter. In your config, rate limiting is applied here in : `GatewayRoutesConfig`

```
            .filter(customRequestRateLimiterGatewayFilterFactory
                    .apply(new RequestRateLimiterGatewayFilterFactory.Config()))
```

Why this is the recommended design
Gateway best practice:

    - Public / bootstrap endpoints → ❌ no rate limiting
        (/init, health checks, callbacks)

    - Business APIs → ✅ rate limited
    (/customers/**, /orders/**)

Other enhancements not implemented here but useful in real-world scenarios:

    - Per-route limits Different limits per API:
        orders  → 5 rps
        customers → 20 rps
        Why useful:
            - Login ≠ heavy APIs
            - Protect expensive endpoints more aggressively

    - Token cost per request
        Consume more tokens for expensive APIs:
```
        config.setRequestedTokens(5);
```
        