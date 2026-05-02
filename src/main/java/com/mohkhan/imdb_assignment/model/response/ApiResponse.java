package com.mohkhan.imdb_assignment.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;

/**
 * @author Moh Khandan
 * Date: 5/1/2026
 * Time: 5:01 PM
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final String status;
    private final T data;
    private final ApiError error;
    private final long requestCount;
    private final Instant timestamp = Instant.now();

    private ApiResponse(String status, T data, ApiError error, long requestCount) {
        this.status = status;
        this.data = data;
        this.error = error;
        this.requestCount = requestCount;
    }

    public static <T> ApiResponse<T> success(T data, long requestCount) {
        return new ApiResponse<>("success", data, null, requestCount);
    }

    public static <T> ApiResponse<T> error(ApiError apiError, long requestCount) {
        return new ApiResponse<>("error", null, apiError, requestCount);
    }
}

