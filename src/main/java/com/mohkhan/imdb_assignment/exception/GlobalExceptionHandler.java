package com.mohkhan.imdb_assignment.exception;

import com.mohkhan.imdb_assignment.filter.RequestCounterFilter;
import com.mohkhan.imdb_assignment.model.response.ApiError;
import com.mohkhan.imdb_assignment.model.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * @author Moh Khandan
 * Date: 5/1/2026
 * Time: 5:10 PM
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final RequestCounterFilter requestCounterFilter;

    @ExceptionHandler(DataNotReadyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataNotReady(DataNotReadyException ex) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        ApiError.of("DATA_NOT_READY", ex.getMessage()),
                        requestCounterFilter.getCount()
                ));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(InvalidRequestException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        ApiError.of("INVALID_REQUEST", ex.getMessage()),
                        requestCounterFilter.getCount()
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(
            MissingServletRequestParameterException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        ApiError.of("MISSING_PARAMETER",
                                "Required parameter missing: " + ex.getParameterName()),
                        requestCounterFilter.getCount()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        ApiError.of("INTERNAL_ERROR", "An unexpected error occurred."),
                        requestCounterFilter.getCount()
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.notFound().build();
    }
}

