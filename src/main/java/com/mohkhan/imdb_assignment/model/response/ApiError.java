package com.mohkhan.imdb_assignment.model.response;

/**
 * @author Moh Khandan
 * Date: 5/1/2026
 * Time: 5:02 PM
 */
public record ApiError(String code, String message) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message);
    }
}