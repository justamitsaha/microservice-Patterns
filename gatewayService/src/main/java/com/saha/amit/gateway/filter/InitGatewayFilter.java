package com.saha.amit.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public class InitGatewayFilter implements GatewayFilter {

    private static final Logger logger = LoggerFactory.getLogger(InitGatewayFilter.class);
    private final ReactiveStringRedisTemplate redisTemplate;

    public InitGatewayFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. Generate secure client_token
        String clientToken = UUID.randomUUID().toString();
        
        // 2. Extract metadata
        String ip = exchange.getRequest().getRemoteAddress() != null ? 
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
        
        // 3. Store in Redis (token -> metadata mapping) for abuse detection
        String redisKey = "client_metadata:" + clientToken;
        String metadata = String.format("{\"ip\":\"%s\",\"ua\":\"%s\"}", ip, userAgent);
        
        return redisTemplate.opsForValue().set(redisKey, metadata, Duration.ofHours(24))
            .then(Mono.defer(() -> {
                // 4. Set HttpOnly Cookie
                ResponseCookie cookie = ResponseCookie.from("client_token", clientToken)
                        .httpOnly(true)
                        .path("/")
                        .maxAge(Duration.ofHours(24))
                        .sameSite("Strict")
                        .build();
                
                exchange.getResponse().addCookie(cookie);
                
                // 5. Build response body
                byte[] body = String.format("{\"message\":\"Initialization successful\",\"token_issued\":true}").getBytes(StandardCharsets.UTF_8);
                
                exchange.getResponse().setStatusCode(HttpStatus.OK);
                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                
                logger.info("✅ Issued client_token for IP={} and stored metadata in Redis", ip);
                
                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
                return exchange.getResponse().writeWith(Mono.just(buffer));
            }));
    }
}
