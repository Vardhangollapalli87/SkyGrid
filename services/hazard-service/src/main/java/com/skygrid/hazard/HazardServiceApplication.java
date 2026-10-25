package com.skygrid.hazard;

import com.skygrid.hazard.config.WeatherProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WeatherProperties.class)
public class HazardServiceApplication {
    public static void main(String[] args) { SpringApplication.run(HazardServiceApplication.class, args); }
}
