package com.skygrid.hazard.api;

import com.skygrid.hazard.provider.WeatherProviderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class WeatherExceptionHandler {
    @ExceptionHandler(WeatherProviderException.class)
    ResponseEntity<ProblemDetail> provider(WeatherProviderException error) {
        HttpStatus status = HttpStatus.valueOf(error.status());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, error.getMessage());
        detail.setTitle(status.is4xxClientError() ? "Invalid weather assessment" : "Weather provider unavailable");
        return ResponseEntity.status(status).body(detail);
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalid(Exception ignored) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The assessment request is invalid.");
        detail.setTitle("Invalid request");
        return ResponseEntity.badRequest().body(detail);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> internal(Exception ignored) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "The weather assessment could not be completed.");
        detail.setTitle("Assessment failed");
        return ResponseEntity.internalServerError().body(detail);
    }
}
