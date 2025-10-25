package com.saha.amit.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.List;

@Component
public class JwtAuthorizationFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthorizationFilter.class);
    private static final String SECRET_KEY = "oycBHAYRCc8fMxKkRVx9FA4EC+pWAgmeRGxQFbLGb5Y=";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
//        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
//            return chain.filter(exchange); // skip preflight
//        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No JWT token found in Authorization header for path {}", exchange.getRequest().getPath());
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);
        log.info("🔐 Validating JWT token for request to {}", token);

        try {
            Key key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET_KEY));
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String userId = claims.getSubject();
            String email = claims.get("email", String.class);
            String issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().toString() : "N/A";
            String expiry = claims.getExpiration() != null ? claims.getExpiration().toString() : "N/A";

            log.info("✅ JWT validated successfully for userId={} email={} issuedAt={} expiresAt={}",
                    userId, email, issuedAt, expiry);

            // Log all custom claims
            claims.forEach((keyName, value) ->
                    log.info("  -> Claim [{}] = {}", keyName, value)
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            email != null ? email : userId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );

            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

        } catch (ExpiredJwtException e) {
            log.warn("❌ JWT expired for token issued to {}. Expired at {}", e.getClaims().getSubject(), e.getClaims().getExpiration());
            return respondUnauthorized(exchange, "Token expired");

        } catch (SignatureException e) {
            log.error("❌ Invalid JWT signature: {}", e.getMessage());
            return respondUnauthorized(exchange, "Invalid token signature");

        } catch (MalformedJwtException e) {
            log.error("❌ Malformed JWT: {}", e.getMessage());
            return respondUnauthorized(exchange, "Malformed token");

        } catch (Exception e) {
            log.error("❌ JWT parsing/validation failed: {}", e.getMessage());
            return respondUnauthorized(exchange, "Invalid or expired token");
        }
    }

    private Mono<Void> respondUnauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        byte[] bytes = String.format("{\"error\":\"%s\"}", message).getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory().wrap(bytes)));
    }
}
