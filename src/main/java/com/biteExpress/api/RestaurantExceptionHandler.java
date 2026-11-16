package com.biteExpress.api;

import com.biteExpress.service.RestaurantNotFoundException;
import com.biteExpress.service.InvalidRestaurantSearchException;
import com.biteExpress.service.UnsupportedRestaurantSearchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class RestaurantExceptionHandler {
    @ExceptionHandler(RestaurantNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(RestaurantNotFoundException error) {
        return problem(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", error.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException error) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Restaurant request validation failed");
        problem.setTitle("Validation failed");
        problem.setProperty("code", "VALIDATION_FAILED");
        List<String> fields = error.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .sorted().toList();
        problem.setProperty("errors", fields);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> invalidBody() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body must be valid restaurant JSON");
    }

    @ExceptionHandler(InvalidRestaurantSearchException.class)
    ResponseEntity<ProblemDetail> invalidSearch(InvalidRestaurantSearchException error) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_FILTER", error.getMessage());
    }

    @ExceptionHandler(UnsupportedRestaurantSearchException.class)
    ResponseEntity<ProblemDetail> unsupportedSearch(UnsupportedRestaurantSearchException error) {
        return problem(HttpStatus.BAD_REQUEST, "UNSUPPORTED_SEARCH", error.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> invalidParameter(MethodArgumentTypeMismatchException error) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "Invalid query parameter: " + error.getName());
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(status.getReasonPhrase());
        body.setProperty("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
