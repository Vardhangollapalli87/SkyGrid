package com.skygrid.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skygrid.gateway.filter.CorrelationIdFilter;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SafeGatewayErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public SafeGatewayErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        boolean timedOut = hasCause(error, TimeoutException.class)
                || hasCause(error, io.netty.handler.timeout.ReadTimeoutException.class);
        HttpStatus status = timedOut ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
        String message = timedOut ? "The upstream service timed out."
                : "The upstream service is unavailable.";
        String correlationId = exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdFilter.HEADER_NAME);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(Map.of(
                    "status", status.value(),
                    "error", message,
                    "correlationId", correlationId == null ? "unavailable" : correlationId));
        } catch (JsonProcessingException serializationError) {
            body = ("{\"status\":" + status.value() + ",\"error\":\"Gateway request failed.\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(body)));
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }
}
