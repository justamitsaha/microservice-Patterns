package com.saha.amit.gateway.filter;

import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageManager;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ClientIdBaggageFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(ClientIdBaggageFilter.class);
    private final Tracer tracer;
    private final BaggageManager baggageManager;

    public ClientIdBaggageFilter(Tracer tracer, BaggageManager baggageManager) {
        this.tracer = tracer;
        this.baggageManager = baggageManager;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String clientId = exchange.getRequest()
                .getHeaders()
                .getFirst("X-Client-Id");

        logger.debug("➡️ Gateway received request {} with X-Client-Id={}", path, clientId);

        if (clientId == null) {
            return chain.filter(exchange);
        }

        // Add clientId to baggage in the current span
        return Mono.deferContextual(ctx -> {
            // Get current span and add baggage
            if (tracer.currentSpan() != null) {
                Baggage baggage = baggageManager.createBaggage("clientId", clientId);
                baggage.makeCurrent();
                logger.debug("✅ Set clientId={} in baggage", clientId);
            }
            return chain.filter(exchange);
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
