package com.skygrid.mission.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class MissionCorsConfigurationTest {

    @Test
    void allowsConfiguredFrontendOriginForApiPreflight() throws Exception {
        var environment = new MockEnvironment().withProperty("CORS_ALLOWED_ORIGINS", "https://skygrid.example, http://localhost:5173");
        CorsFilter filter = new MissionCorsConfiguration().missionCorsFilter(environment);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/missions");
        request.addHeader("Origin", "https://skygrid.example");
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("https://skygrid.example", response.getHeader("Access-Control-Allow-Origin"));
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("POST"));
    }

    @Test
    void rejectsUnconfiguredFrontendOrigin() throws Exception {
        var environment = new MockEnvironment().withProperty("CORS_ALLOWED_ORIGINS", "https://skygrid.example");
        CorsFilter filter = new MissionCorsConfiguration().missionCorsFilter(environment);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/missions");
        request.addHeader("Origin", "https://other.example");
        request.addHeader("Access-Control-Request-Method", "POST");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNull(response.getHeader("Access-Control-Allow-Origin"));
    }
}
