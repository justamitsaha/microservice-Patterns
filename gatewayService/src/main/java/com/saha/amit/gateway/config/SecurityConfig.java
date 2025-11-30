package com.saha.amit.gateway.config;

import com.saha.amit.gateway.filter.JwtAuthorizationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtAuthorizationFilter jwtAuthorizationFilter;

    public SecurityConfig(JwtAuthorizationFilter jwtAuthorizationFilter) {
        this.jwtAuthorizationFilter = jwtAuthorizationFilter;
    }


    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        return http
                // ✅ Enable CORS
                //.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // 🧩 Disable Basic Auth pop-up
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                // 🧩 Disable Form login
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // 🧩 No session — stateless
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                // 🧩 Custom entry point for unauthorized requests (no "WWW-Authenticate")
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // login APIs
                        .pathMatchers(HttpMethod.POST, "/customers/login").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/customers/login").permitAll()

                        // REGISTRATION APIs
                        .pathMatchers(HttpMethod.POST, "/customers").permitAll()
                        .pathMatchers(HttpMethod.POST, "/customers/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/customers").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/customers/**").permitAll()

                        .pathMatchers("/public/**", "/actuator/**", "/fallback/**").permitAll()
                        .anyExchange().authenticated()
                )
                // Add JWT filter
                .addFilterAt(jwtAuthorizationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    /*
    This bean is not needed as Spring Cloud Gateway global CORS is already configured in application.properties
    // ✅ CORS bean defined above
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(List.of("http://localhost:4200"));
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfig.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        corsConfig.setExposedHeaders(List.of("Authorization"));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return source;
    }*/
}

