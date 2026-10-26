package com.skygrid.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER_NAME = "X-Correlation-ID";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String supplied = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        String correlationId = supplied != null && SAFE_ID.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();

        exchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER_NAME, correlationId))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}
