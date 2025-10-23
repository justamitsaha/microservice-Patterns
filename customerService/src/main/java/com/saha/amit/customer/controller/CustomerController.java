package com.saha.amit.customer.controller;

import com.saha.amit.customer.dto.CustomerRequest;
import com.saha.amit.customer.dto.CustomerResponse;
import com.saha.amit.customer.dto.LoginRequest;
import com.saha.amit.customer.dto.LoginResponse;
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
        return repository.findByEmail(request.getEmail())
                .map(user -> {
                    String recomputed = CustomerServiceUtil.hashPassword(request.getPassword(), user.getPasswordSalt());
                    boolean ok = CustomerServiceUtil.constantTimeEquals(user.getPasswordHash(), recomputed);

                    if (!ok) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of("message", "Invalid credentials"));
                    }

                    String accessToken = customerServiceUtil.generateAccessToken(user.getId(), user.getEmail());
                    String refreshToken = customerServiceUtil.generateRefreshToken(user.getId(), user.getEmail());

                    ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                            .httpOnly(true)
                            .secure(true)
                            .path("/customers/refresh") // cookie sent only to refresh endpoint
                            .sameSite("Strict")
                            .maxAge(Duration.ofDays(7))
                            .build();

                    return ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, cookie.toString())
                            .body(Map.of(
                                    "message", "Login successful",
                                    "accessToken", accessToken
                            ));
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found")));
    }


    @PostMapping("/refresh")
    public Mono<ResponseEntity<Map<String, String>>> refresh(@CookieValue("refreshToken") String refreshToken) {
        try {
            Claims claims = customerServiceUtil.validateToken(refreshToken);
            if (!"refresh".equals(claims.get("type"))) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid token type")));
            }

            String userId = claims.getSubject();
            String email = (String) claims.get("email");

            String newAccess = customerServiceUtil.generateAccessToken(userId, email);
            return Mono.just(ResponseEntity.ok(Map.of(
                    "accessToken", newAccess,
                    "message", "Token refreshed"
            )));
        } catch (Exception e) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired refresh token")));
        }
    }


}
