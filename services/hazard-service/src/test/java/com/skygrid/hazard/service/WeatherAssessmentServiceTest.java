package com.skygrid.hazard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skygrid.hazard.api.WeatherAssessmentRequest;
import com.skygrid.hazard.config.WeatherProperties;
import com.skygrid.hazard.provider.WeatherProvider;
import com.skygrid.hazard.provider.WeatherProviderException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeatherAssessmentServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WeatherProperties properties = new WeatherProperties("https://weather.invalid", Duration.ofSeconds(1), Duration.ofSeconds(1),
            72, 250, 12, 25, new WeatherProperties.Thresholds(12, 17, 2), "*");

    @Test void returnsWarningsAndFieldVariationAcrossEveryRouteSample() throws Exception {
        WeatherProvider provider = mock(WeatherProvider.class);
        Instant time = Instant.now().plusSeconds(600);
        when(provider.forecast(anyList(), eq(24))).thenAnswer(invocation -> {
            List<com.skygrid.hazard.api.WeatherAssessmentResponse.Coordinate> coordinates = invocation.getArgument(0);
            return coordinates.stream().map(c -> new WeatherProvider.Forecast(IntStream.range(0, 24).mapToObj(hour ->
                    new WeatherProvider.ForecastValue(time.plusSeconds(hour * 3600L), 20.0,
                            c.longitude() > 0 ? 14.0 : 8.0, 18.0, 3.0)).toList(), "GMT")).toList();
        });
        var request = new WeatherAssessmentRequest(WeatherAssessmentRequest.AssessmentType.ROUTE,
                mapper.readTree("{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,0]]}"), null);
        var result = new WeatherAssessmentService(provider, properties).assess(request);
        assertTrue(result.samples().size() > 1);
        assertEquals("COMPLETE", result.status());
        assertEquals("UTC", result.timezone());
        assertEquals(8.0, result.variation().stream().filter(s -> s.field().equals("windSpeed")).findFirst().orElseThrow().minimum());
        assertTrue(result.warnings().stream().anyMatch(w -> w.code().equals("WIND_GUST_HIGH") && w.measuredValue() == 18.0));
        verify(provider).forecast(anyList(), eq(24));
    }

    @Test void rejectsHorizonAboveConfiguredMaximum() throws Exception {
        WeatherProvider provider = mock(WeatherProvider.class);
        var request = new WeatherAssessmentRequest(WeatherAssessmentRequest.AssessmentType.POINT,
                mapper.readTree("{\"type\":\"Point\",\"coordinates\":[0,0]}"), 73);
        assertThrows(WeatherProviderException.class, () -> new WeatherAssessmentService(provider, properties).assess(request));
        verifyNoInteractions(provider);
    }

    @Test void marksShortOrMissingProviderForecastValuesPartialWithoutReplacingNulls() throws Exception {
        WeatherProvider provider = mock(WeatherProvider.class);
        Instant time = Instant.now().plusSeconds(600);
        when(provider.forecast(anyList(), eq(1))).thenReturn(List.of(new WeatherProvider.Forecast(List.of(
                new WeatherProvider.ForecastValue(time, null, 4.0, null, null)), "UTC")));
        var request = new WeatherAssessmentRequest(WeatherAssessmentRequest.AssessmentType.POINT,
                mapper.readTree("{\"type\":\"Point\",\"coordinates\":[0,0]}"), 1);
        var result = new WeatherAssessmentService(provider, properties).assess(request);
        assertEquals("PARTIAL", result.status());
        assertNull(result.samples().getFirst().forecast().getFirst().temperatureC());
        assertNull(result.variation().stream().filter(field -> field.field().equals("temperature")).findFirst().orElseThrow().minimum());
    }
}
