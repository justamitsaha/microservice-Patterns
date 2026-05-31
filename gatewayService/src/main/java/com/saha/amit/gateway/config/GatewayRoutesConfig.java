package com.saha.amit.gateway.config;

import com.saha.amit.gateway.filter.InitGatewayFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Configuration
public class GatewayRoutesConfig {

    private static final Logger logger = LoggerFactory.getLogger(GatewayRoutesConfig.class);
    private final ReactiveStringRedisTemplate redisTemplate;

    public GatewayRoutesConfig(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

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

    // Existing KeyResolver for backward compatibility or authenticated requests
    /*@Bean
    public KeyResolver clientIdKeyResolver() {
        return exchange -> {
            String clientId =
                    exchange.getRequest().getHeaders().getFirst("X-Client-Id");
            logger.debug("🔑 KeyResolver resolved key = {}", clientId);
            return Mono.justOrEmpty(clientId);
        };
    }*/


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

    @Bean
    public GlobalFilter logClientIdFilter() {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getPath().value();
            HttpCookie cookie = exchange.getRequest().getCookies().getFirst("client_token");
            String token = (cookie != null) ? cookie.getValue() : "none";
            logger.debug("➡️ Gateway received request {} with client_token={}", path, token);
            return chain.filter(exchange);
        };
    }


    // 4️⃣ Define all routes and apply tiered rate limiting
    @Bean
    public RouteLocator customRouteLocator(
            RouteLocatorBuilder routes,
            RequestRateLimiterGatewayFilterFactory rateLimiterFactory,
            KeyResolver cookieKeyResolver,
            KeyResolver ipKeyResolver,
            RedisRateLimiter tokenRateLimiter,
            RedisRateLimiter ipRateLimiter) {

        return routes.routes()
                // Orders route (Still uses basic rate limiting for now)
                .route("orders", r -> r
                        .path("/orders/**")
                        .filters(f -> f
                                .addRequestHeader("X-From-Gateway", "true")
                                .addResponseHeader("X-Gateway", "spring-cloud-gateway")
                                .retry(config -> config.setRetries(3)
                                        .setStatuses(HttpStatus.INTERNAL_SERVER_ERROR,
                                                HttpStatus.BAD_GATEWAY,
                                                HttpStatus.SERVICE_UNAVAILABLE))
                                .circuitBreaker(cb -> cb.setName("ordersCb")
                                        .setFallbackUri("forward:/fallback/orders"))
                        )
                        .uri("lb://order-service"))

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

                // Main Customers route
                .route("customers", r -> r
                        .path("/customers/**")
                        .filters(f -> f
                                .addRequestHeader("X-From-Gateway", "true")
                                .addResponseHeader("X-Gateway", "spring-cloud-gateway")
                                .circuitBreaker(cb -> cb.setName("customersCb")
                                        .setFallbackUri("forward:/fallback/customers")
                                        .addStatusCode("500")
                                        .addStatusCode("501")
                                        .addStatusCode("503"))
                                .retry(config -> config
                                        .setRetries(2)
                                        .setMethods(HttpMethod.GET)
                                        .setStatuses(
                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                HttpStatus.BAD_GATEWAY,
                                                HttpStatus.SERVICE_UNAVAILABLE,
                                                HttpStatus.GATEWAY_TIMEOUT)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofMillis(1000), 2, true))
                                // Protect authenticated/session customers too
                                .requestRateLimiter(c -> c.setRateLimiter(tokenRateLimiter).setKeyResolver(cookieKeyResolver))
                        )
                        .uri("lb://customer-service"))

                //Init route
                .route("init", r -> r
                        .path("/init")
                        .filters(f -> f.filter(new InitGatewayFilter(redisTemplate)))
                        .uri("no://op")
                )
                .build();
    }
}
