package com.skygrid.hazard.api;

import com.skygrid.hazard.service.WeatherAssessmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hazards")
public class WeatherAssessmentController {
    private final WeatherAssessmentService service;
    public WeatherAssessmentController(WeatherAssessmentService service) { this.service = service; }

    @PostMapping("/weather-assessment")
    public WeatherAssessmentResponse assess(@Valid @RequestBody WeatherAssessmentRequest request) { return service.assess(request); }
}
