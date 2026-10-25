package com.skygrid.hazard.api;

import com.skygrid.hazard.provider.WeatherProviderException;
import com.skygrid.hazard.service.WeatherAssessmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WeatherAssessmentControllerTest {
    @Mock WeatherAssessmentService service;
    private MockMvc mvc;

    @Test void requestAndResponseStringFormsDoNotIncludeGeometryOrForecastPayload() throws Exception {
        var geometry = new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"type\":\"Point\",\"coordinates\":[77.5,12.9]}");
        var request = new WeatherAssessmentRequest(WeatherAssessmentRequest.AssessmentType.POINT, geometry, 24);
        var response = new WeatherAssessmentResponse(UUID.randomUUID(), "COMPLETE", "provider",
                Instant.parse("2030-01-01T00:00:00Z"), "UTC", 24, "point",
                new WeatherAssessmentResponse.Thresholds(12, 17, 2),
                List.of(new WeatherAssessmentResponse.Sample(1, new WeatherAssessmentResponse.Coordinate(77.5, 12.9), List.of())),
                List.of(), List.of(), List.of());
        org.junit.jupiter.api.Assertions.assertFalse(request.toString().contains("77.5"));
        org.junit.jupiter.api.Assertions.assertFalse(response.toString().contains("77.5"));
    }

    @BeforeEach void setup() {
        var validator = new LocalValidatorFactoryBean(); validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new WeatherAssessmentController(service))
                .setControllerAdvice(new WeatherExceptionHandler()).setValidator(validator).build();
    }

    @Test void acceptsValidPointRequestAndReturnsStableContract() throws Exception {
        when(service.assess(any())).thenReturn(new WeatherAssessmentResponse(UUID.randomUUID(), "COMPLETE", "Open-Meteo Forecast API",
                Instant.parse("2030-01-01T00:00:00Z"), "UTC", 24, "point",
                new WeatherAssessmentResponse.Thresholds(12, 17, 2), List.of(), List.of(), List.of(), List.of()));
        mvc.perform(post("/api/v1/hazards/weather-assessment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assessmentType\":\"POINT\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[0,0]}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.provider").value("Open-Meteo Forecast API"))
                .andExpect(jsonPath("$.timezone").value("UTC"));
    }

    @Test void rejectsMissingFieldsAndMapsProviderFailureSafely() throws Exception {
        mvc.perform(post("/api/v1/hazards/weather-assessment").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        when(service.assess(any())).thenThrow(new WeatherProviderException(503, "Weather provider could not be reached after bounded retries."));
        mvc.perform(post("/api/v1/hazards/weather-assessment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assessmentType\":\"POINT\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[0,0]}}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.title").value("Weather provider unavailable"));
    }
}
