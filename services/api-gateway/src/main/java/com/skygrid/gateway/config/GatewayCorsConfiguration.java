package com.skygrid.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class GatewayCorsConfiguration {

    @Bean
    CorsWebFilter gatewayCorsWebFilter(Environment environment) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(environment
                        .getProperty("CORS_ALLOWED_ORIGINS", "http://localhost:5173").split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Accept", "Content-Type", "X-Correlation-ID"));
        cors.setExposedHeaders(List.of("X-Correlation-ID"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return new CorsWebFilter(source);
    }
}
