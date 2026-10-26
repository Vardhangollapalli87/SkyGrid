package com.skygrid.mission.api;

import com.skygrid.mission.mission.MissionNotFoundException;
import com.skygrid.mission.mission.InvalidPaginationException;
import com.skygrid.mission.mission.InvalidMissionGeometryException;
import com.skygrid.mission.mission.MissionGeometryRequiredException;
import com.skygrid.mission.mission.RouteAssociationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.net.URI;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RouteAssociationException.class)
    public ResponseEntity<ProblemDetail> handleRouteAssociation(RouteAssociationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Route association conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(MissionNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(MissionNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "The requested mission was not found.");
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Mission not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(InvalidPaginationException.class)
    public ResponseEntity<ProblemDetail> handleInvalidPagination() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Page must be non-negative and size must be between 1 and 100.");
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Invalid pagination parameters");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(InvalidMissionGeometryException.class)
    public ResponseEntity<ProblemDetail> handleInvalidMissionGeometry(InvalidMissionGeometryException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Invalid mission geometry");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(MissionGeometryRequiredException.class)
    public ResponseEntity<ProblemDetail> handleMissionGeometryRequired(MissionGeometryRequiredException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Mission geometry required");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more fields are invalid.");
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Validation failed");
        problem.setProperty("errors", violations);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request body is missing or malformed.");
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Malformed request");
        return ResponseEntity.badRequest().body(problem);
    }

    private record FieldViolation(String field, String message) {
    }
}
