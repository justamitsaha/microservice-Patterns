package com.saha.amit.customer.controller;

import com.saha.amit.customer.dto.CustomerRequest;
import com.saha.amit.customer.dto.CustomerResponse;
import com.saha.amit.customer.dto.LoginRequest;
import com.saha.amit.customer.model.CustomerEntity;
import com.saha.amit.customer.repository.CustomerRepository;
import com.saha.amit.customer.service.CustomerService;
import com.saha.amit.customer.util.CustomerServiceUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService service;
    private final CustomerRepository repository;
    private final CustomerServiceUtil customerServiceUtil;

    @GetMapping
    public ResponseEntity<Flux<CustomerEntity>> list() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<CustomerResponse>> get(@PathVariable String id) {
        log.info("Fetching customer with ID: {}", id);
        return service.getWithOrders(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Mono<CustomerEntity>> register(@RequestBody CustomerRequest request) {
        log.info("Registering new customer with email: {}", request);
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<CustomerEntity>> update(@PathVariable String id, @RequestBody CustomerRequest request) {
        return service.update(id, request)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String id) {
        return service.findById(id)
                .flatMap(existing -> service.delete(id).thenReturn(ResponseEntity.noContent().<Void>build()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, String>>> login(@RequestBody LoginRequest request) {
        String emailAttempt = request.getEmail();
        log.info("🔐 Login attempt for email: {}", emailAttempt);

        return repository.findByEmail(emailAttempt)
                .map(user -> {
                    String recomputed = CustomerServiceUtil.hashPassword(request.getPassword(), user.getPasswordSalt());
                    boolean ok = CustomerServiceUtil.constantTimeEquals(user.getPasswordHash(), recomputed);

                    if (!ok) {
                        log.warn("❌ Invalid credentials for email: {}", emailAttempt);
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of("message", "Invalid credentials"));
                    }

                    // ✅ Credentials OK → Generate JWT tokens
                    String accessToken = customerServiceUtil.generateAccessToken(String.valueOf(user.getId()), user.getEmail());
                    String refreshToken = customerServiceUtil.generateRefreshToken(String.valueOf(user.getId()), user.getEmail());
                    log.info("Access token {}", accessToken);
                    // Extract claims to log non-sensitive details
                    try {
                        Claims accessClaims = customerServiceUtil.validateToken(accessToken);
                        log.info("✅ Access token issued for userId={} email={} issuedAt={} expiresAt={}",
                                accessClaims.getSubject(),
                                accessClaims.get("email", String.class),
                                accessClaims.getIssuedAt(),
                                accessClaims.getExpiration());
                    } catch (Exception e) {
                        log.error("⚠️ Failed to parse generated access token for email {}: {}", emailAttempt, e.getMessage());
                    }

                    ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                            .httpOnly(true)
                            .secure(true)
                            .path("/customers/refresh")
                            .sameSite("Strict")
                            .maxAge(Duration.ofDays(7))
                            .build();

                    log.info("💾 Refresh token cookie set for userId={} (HttpOnly, 7 days validity)", user.getId());
                    return ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, cookie.toString())
                            .body(Map.of(
                                    "message", "Login successful",
                                    "accessToken", accessToken
                            ));
                })
                .defaultIfEmpty(
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of("message", "User not found"))
                )
                .doOnSuccess(resp -> {
                    if (resp.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                        log.warn("🚫 Login failed for email: {} (User not found or unauthorized)", emailAttempt);
                    }
                });
    }


    @PostMapping("/refresh")
    public Mono<ResponseEntity<Map<String, String>>> refresh(@CookieValue("refreshToken") String refreshToken) {
        log.info("♻️ Refresh token request received");

        try {
            Claims claims = customerServiceUtil.validateToken(refreshToken);
            String tokenType = (String) claims.get("type");

            if (!"refresh".equals(tokenType)) {
                log.warn("❌ Invalid token type received: {}", tokenType);
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid token type")));
            }

            String userId = claims.getSubject();
            String email = claims.get("email", String.class);
            log.info("✅ Valid refresh token for userId={} email={} expiresAt={}", userId, email, claims.getExpiration());

            String newAccess = customerServiceUtil.generateAccessToken(userId, email);
            Claims newAccessClaims = customerServiceUtil.validateToken(newAccess);

            log.info("🎟️ New access token generated for userId={} expiresAt={}",
                    newAccessClaims.getSubject(), newAccessClaims.getExpiration());

            return Mono.just(ResponseEntity.ok(Map.of(
                    "accessToken", newAccess,
                    "message", "Token refreshed"
            )));
        } catch (Exception e) {
            log.error("❌ Refresh token validation failed: {}", e.getMessage());
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired refresh token")));
        }
    }


}
