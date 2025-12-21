package com.saha.amit.gateway.config;

import com.saha.amit.gateway.filter.InitGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayRoutesConfig {

    // 1️⃣ Simple key resolver — rate limit all requests by "anonymous" user
    @Bean
    public KeyResolver clientIdKeyResolver() {
        return exchange ->
                Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("X-Client-Id"))
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Missing X-Client-Id"
                        )));
    }


    // 2️⃣ Redis rate limiter — 10 requests/sec, burst up to 20
    @Bean
    public RedisRateLimiter clientRateLimiter() {
        return new RedisRateLimiter(
                10, 20
        );
    }


    // 3️⃣ Custom factory bean (renamed to avoid conflict with GatewayAutoConfiguration)
    @Bean
    public RequestRateLimiterGatewayFilterFactory customRequestRateLimiterGatewayFilterFactory(
            RedisRateLimiter clientRateLimiter,
            KeyResolver clientIdKeyResolver) {

        RequestRateLimiterGatewayFilterFactory factory =
                new RequestRateLimiterGatewayFilterFactory(
                        clientRateLimiter,
                        clientIdKeyResolver
                );

        factory.setEmptyKeyStatusCode(HttpStatus.TOO_MANY_REQUESTS.name());
        return factory;
    }


    // 4️⃣ Define all routes and apply rate limiter
    @Bean
    public RouteLocator customRouteLocator(
            RouteLocatorBuilder routes,
            RequestRateLimiterGatewayFilterFactory customRequestRateLimiterGatewayFilterFactory) {

        return routes.routes()
                // Orders route
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
                                // ✅ Apply custom rate limiter
                                .filter(customRequestRateLimiterGatewayFilterFactory
                                        .apply(new RequestRateLimiterGatewayFilterFactory.Config()))
                        )
                        .uri("lb://order-service"))

                // Customers route
                .route("customers", r -> r
                        .path("/customers/**")
                        .filters(f -> f
                                .addRequestHeader("X-From-Gateway", "true")
                                .retry(config -> config.setRetries(2)
                                        .setStatuses(HttpStatus.INTERNAL_SERVER_ERROR,
                                                HttpStatus.BAD_GATEWAY,
                                                HttpStatus.SERVICE_UNAVAILABLE))
                                .circuitBreaker(cb -> cb.setName("customersCb")
                                        .setFallbackUri("forward:/fallback/customers"))
                                // ✅ Apply custom rate limiter
                                .filter(customRequestRateLimiterGatewayFilterFactory
                                        .apply(new RequestRateLimiterGatewayFilterFactory.Config()))
                        )
                        .uri("lb://customer-service"))

                // Orders API prefix rewrite
                .route("orders-api", r -> r
                        .path("/api/orders/{segment}", "/api/orders/{segment}/**")
                        .filters(f -> f.rewritePath("/api/orders/(?<segment>.*)", "/orders/${segment}"))
                        .uri("lb://order-service"))

                // Customers API prefix rewrite
                .route("customers-api", r -> r
                        .path("/api/customers/{segment}", "/api/customers/{segment}/**")
                        .filters(f -> f.rewritePath("/api/customers/(?<segment>.*)", "/customers/${segment}"))
                        .uri("lb://customer-service"))
                .route("init", r -> r
                        .path("/init")
                        .filters(f -> f.filter(new InitGatewayFilter()))
                        .uri("no://op") // important
                )
                .build();
    }
}
