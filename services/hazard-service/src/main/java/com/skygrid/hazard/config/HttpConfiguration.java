package com.skygrid.hazard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class HttpConfiguration {
    @Bean RestClient weatherRestClient(RestClient.Builder builder, WeatherProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return builder.requestFactory(factory).build();
    }

    @Bean WebMvcConfigurer corsConfigurer(WeatherProperties properties) {
        return new WebMvcConfigurer() {
            @Override public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**").allowedOrigins(java.util.Arrays.stream(properties.corsAllowedOrigins().split(","))
                                .map(String::trim).filter(origin -> !origin.isEmpty()).toArray(String[]::new))
                        .allowedMethods("GET", "POST", "OPTIONS").allowedHeaders("Content-Type", "Accept");
            }
        };
    }
}
