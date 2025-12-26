package com.saha.amit.gateway.filter;

import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class InitGatewayFilter implements GatewayFilter {

    private static final Logger logger = LoggerFactory.getLogger(InitGatewayFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String clientId = UUID.randomUUID().toString();

        exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.valueOf(MediaType.APPLICATION_JSON));

        byte[] body = String.format(
                "{\"clientId\":\"%s\"}", clientId
        ).getBytes(StandardCharsets.UTF_8);

        logger.debug("Generated clientId={} for /init request", clientId);

        DataBuffer buffer =
                exchange.getResponse().bufferFactory().wrap(body);

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
