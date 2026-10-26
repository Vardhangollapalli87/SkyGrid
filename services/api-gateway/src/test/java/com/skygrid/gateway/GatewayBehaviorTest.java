package com.skygrid.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skygrid.gateway.error.SafeGatewayErrorHandler;
import com.skygrid.gateway.filter.CorrelationIdFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "CORS_ALLOWED_ORIGINS=https://skygrid.example.com",
        "debug=false"
})
@AutoConfigureWebTestClient
class GatewayBehaviorTest {

    private static final DisposableServer MOCK_BACKEND = HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .handle((request, response) -> {
                String origin = request.requestHeaders().get("Origin");
                if (origin != null) {
                    response.header("Access-Control-Allow-Origin", origin);
                }
                return response.sendString(Mono.just(request.uri()));
            })
            .bindNow();

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void backendUrls(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + MOCK_BACKEND.port();
        registry.add("MISSION_SERVICE_URL", () -> url);
        registry.add("HAZARD_SERVICE_URL", () -> url);
    }

    @Test
    void forwardsExistingApiPathsWithoutRewriting() {
        assertForwardedPath("/api/v1/missions?page=0");
        assertForwardedPath("/api/v1/routes");
        assertForwardedPath("/api/v1/airspace/datasets");
        assertForwardedPath("/api/v1/hazards/weather-assessment");
    }

    @Test
    void handlesCorsPreflightAtTheGateway() {
        webTestClient.options()
                .uri("http://skygrid.example.com/api/v1/hazards/weather-assessment")
                .header("Origin", "https://skygrid.example.com")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "https://skygrid.example.com")
                .expectHeader().exists("Access-Control-Allow-Methods");
    }

    @Test
    void returnsOneGatewayCorsHeaderAndDoesNotForwardBrowserOrigin() {
        webTestClient.get()
                .uri("http://skygrid.example.com/api/v1/hazards/weather-assessment")
                .header("Origin", "https://skygrid.example.com")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().values("Access-Control-Allow-Origin",
                        values -> assertThat(values).containsExactly("https://skygrid.example.com"));
    }

    @Test
    void returnsSafeErrorBodyWithCorrelationId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/missions").build());
        exchange.getResponse().getHeaders().set(CorrelationIdFilter.HEADER_NAME, "test-correlation-1");
        SafeGatewayErrorHandler handler = new SafeGatewayErrorHandler(new ObjectMapper());

        handler.handle(exchange, new IllegalStateException("private host and credentials must not escape")).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(502);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("test-correlation-1", "upstream service is unavailable")
                .doesNotContain("private host", "credentials");
    }

    @Test
    void addsCorrelationIdToResponses() {
        webTestClient.get()
                .uri("/api/v1/missions/one")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(CorrelationIdFilter.HEADER_NAME,
                        value -> assertThat(UUID.fromString(value)).isNotNull());
    }

    private void assertForwardedPath(String path) {
        webTestClient.get()
                .uri(path)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(path);
    }

    @AfterAll
    static void stopMockBackend() {
        MOCK_BACKEND.disposeNow();
    }
}
