package com.skygrid.hazard.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skygrid.hazard.api.WeatherAssessmentResponse.Coordinate;
import com.skygrid.hazard.config.WeatherProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.http.HttpMethod.GET;

class OpenMeteoClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WeatherProperties properties = new WeatherProperties("https://weather.test/v1/forecast", Duration.ofSeconds(1), Duration.ofSeconds(1),
            72, 250, 12, 25, new WeatherProperties.Thresholds(12, 17, 2), "*");

    @Test void requestsUtcMetresAndParsesAvailableHourlyFieldsWithoutInventingMissingValues() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("timezone=UTC"),
                        org.hamcrest.Matchers.containsString("temperature_unit=celsius"),
                        org.hamcrest.Matchers.containsString("wind_speed_unit=ms"),
                        org.hamcrest.Matchers.containsString("precipitation_unit=mm"))))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                    {"timezone":"GMT","hourly":{"time":["2030-01-01T00:00","2030-01-01T01:00"],"temperature_2m":[12,null],"wind_speed_10m":[13,14],"wind_gusts_10m":[null,20]}}
                    """, MediaType.APPLICATION_JSON));
        OpenMeteoClient client = new OpenMeteoClient(builder.build(), properties);
        var result = client.forecast(List.of(new Coordinate(77, 12)), 12).getFirst();
        assertEquals(12.0, result.values().getFirst().temperatureC());
        assertEquals(java.time.Instant.parse("2030-01-01T00:00:00Z"), result.values().getFirst().validAt());
        assertNull(result.values().getFirst().precipitationMm());
        assertNull(result.values().getFirst().windGustMs());
        assertEquals(20.0, result.values().get(1).windGustMs());
        server.verify();
    }

    @Test void rejectsMalformedProviderTimeAndUnexpectedLocationCount() throws Exception {
        var malformed = mapper.readTree("{\"timezone\":\"UTC\",\"hourly\":{\"time\":[\"invalid\"]}}");
        assertThrows(WeatherProviderException.class, () -> OpenMeteoClient.parse(malformed, 1));
        assertThrows(WeatherProviderException.class, () -> OpenMeteoClient.parse(mapper.readTree("[]"), 1));
    }

    @Test void retriesRateLimitOnlyThreeTimesThenReturnsUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        for (int attempt = 0; attempt < 3; attempt++)
            server.expect(requestTo(org.hamcrest.Matchers.containsString("timezone=UTC")))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        OpenMeteoClient client = new OpenMeteoClient(builder.build(), properties);
        WeatherProviderException failure = assertThrows(WeatherProviderException.class,
                () -> client.forecast(List.of(new Coordinate(77, 12)), 12));
        assertEquals(503, failure.status());
        server.verify();
    }

    @Test void retriesTimeoutOnlyThreeTimesThenReturnsUnavailable() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        for (int attempt = 0; attempt < 3; attempt++)
            server.expect(requestTo(org.hamcrest.Matchers.containsString("timezone=UTC")))
                    .andRespond(withException(new java.net.SocketTimeoutException("simulated timeout")));
        OpenMeteoClient client = new OpenMeteoClient(builder.build(), properties);
        WeatherProviderException failure = assertThrows(WeatherProviderException.class,
                () -> client.forecast(List.of(new Coordinate(77, 12)), 12));
        assertEquals(503, failure.status());
        server.verify();
    }
}
