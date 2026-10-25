package com.skygrid.hazard.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class AssessmentBodyLimitFilter extends OncePerRequestFilter {
    private static final int MAX_BODY_BYTES = 65_536;

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !request.getRequestURI().endsWith("/weather-assessment");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) { tooLarge(response); return; }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096]; int count;
        InputStream input = request.getInputStream();
        while ((count = input.read(buffer)) != -1) {
            if (body.size() + count > MAX_BODY_BYTES) { tooLarge(response); return; }
            body.write(buffer, 0, count);
        }
        byte[] bytes = body.toByteArray();
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    @Override public int read() { return stream.read(); }
                    @Override public boolean isFinished() { return stream.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Async body reads are not supported."); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(getCharacterEncoding()))); }
        }, response);
    }

    private static void tooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(413); response.setContentType("application/problem+json"); response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"title\":\"Request too large\",\"status\":413,\"detail\":\"Assessment request is limited to 64 KiB.\"}");
    }
}
