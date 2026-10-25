package com.skygrid.hazard.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.skygrid.hazard.api.WeatherAssessmentResponse.Coordinate;
import com.skygrid.hazard.config.WeatherProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
public class OpenMeteoClient implements WeatherProvider {
    private static final String PROVIDER = "Open-Meteo Forecast API";
    private static final String HOURLY_FIELDS = "temperature_2m,wind_speed_10m,wind_gusts_10m,precipitation";
    private final RestClient restClient;
    private final WeatherProperties properties;

    public OpenMeteoClient(RestClient weatherRestClient, WeatherProperties properties) {
        this.restClient = weatherRestClient;
        this.properties = properties;
    }

    @Override public List<Forecast> forecast(List<Coordinate> coordinates, int forecastHours) {
        String latitude = coordinates.stream().map(c -> Double.toString(c.latitude())).collect(java.util.stream.Collectors.joining(","));
        String longitude = coordinates.stream().map(c -> Double.toString(c.longitude())).collect(java.util.stream.Collectors.joining(","));
        String uri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .queryParam("latitude", latitude).queryParam("longitude", longitude)
                .queryParam("hourly", HOURLY_FIELDS).queryParam("forecast_hours", forecastHours)
                .queryParam("timezone", "UTC").queryParam("temperature_unit", "celsius")
                .queryParam("wind_speed_unit", "ms").queryParam("precipitation_unit", "mm")
                .build().encode().toUriString();
        RuntimeException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                JsonNode body = restClient.get().uri(uri).retrieve().body(JsonNode.class);
                return parse(body, coordinates.size());
            } catch (RestClientResponseException failure) {
                int code = failure.getStatusCode().value();
                if ((code == 429 || code >= 500) && attempt < 2) { pause(attempt); last = failure; continue; }
                throw new WeatherProviderException(code == 429 ? 503 : 502, "Weather provider returned an unavailable response.", failure);
            } catch (ResourceAccessException failure) {
                last = failure;
                if (attempt == 2) break;
                pause(attempt);
            } catch (WeatherProviderException failure) { throw failure; }
            catch (RuntimeException failure) {
                throw new WeatherProviderException(502, "Weather provider returned malformed forecast data.", failure);
            }
        }
        throw new WeatherProviderException(503, "Weather provider could not be reached after bounded retries.", last);
    }

    static List<Forecast> parse(JsonNode body, int expectedLocations) {
        if (body == null || body.isNull()) throw new WeatherProviderException(502, "Weather provider returned an empty response.");
        List<JsonNode> locations = new ArrayList<>();
        if (body.isArray()) body.forEach(locations::add); else locations.add(body);
        if (locations.size() != expectedLocations) throw new WeatherProviderException(502, "Weather provider returned an unexpected location count.");
        List<Forecast> result = new ArrayList<>();
        for (JsonNode location : locations) result.add(parseLocation(location));
        return List.copyOf(result);
    }

    private static Forecast parseLocation(JsonNode root) {
        JsonNode hourly = root.path("hourly");
        JsonNode times = hourly.path("time");
        if (!times.isArray()) throw new WeatherProviderException(502, "Weather provider forecast times are missing or malformed.");
        List<ForecastValue> values = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            Instant validAt;
            try { validAt = LocalDateTime.parse(times.get(i).asText()).toInstant(ZoneOffset.UTC); }
            catch (RuntimeException failure) { throw new WeatherProviderException(502, "Weather provider returned a malformed forecast time.", failure); }
            values.add(new ForecastValue(validAt, numeric(hourly.path("temperature_2m"), i),
                    numeric(hourly.path("wind_speed_10m"), i), numeric(hourly.path("wind_gusts_10m"), i),
                    numeric(hourly.path("precipitation"), i)));
        }
        JsonNode timezone = root.get("timezone");
        if (timezone == null || !timezone.isTextual()) throw new WeatherProviderException(502, "Weather provider timezone is missing or malformed.");
        return new Forecast(List.copyOf(values), timezone.textValue());
    }

    private static Double numeric(JsonNode series, int index) {
        if (!series.isArray() || index >= series.size() || !series.get(index).isNumber()) return null;
        double value = series.get(index).doubleValue();
        return Double.isFinite(value) ? value : null;
    }

    private static void pause(int attempt) {
        try { Thread.sleep(150L * (attempt + 1)); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new WeatherProviderException(503, "Weather provider retry was interrupted.", interrupted); }
    }
}
