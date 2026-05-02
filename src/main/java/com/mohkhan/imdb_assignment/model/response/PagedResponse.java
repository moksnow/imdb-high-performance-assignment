package com.mohkhan.imdb_assignment.model.response;

import java.util.List;

/**
 * @author Moh Khandan
 * Date: 5/1/2026
 * Time: 5:03 PM
 */
public record PagedResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages, boolean hasNext) {

    public static <T> PagedResponse<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = (int) Math.ceil((double) totalItems / size);
        boolean hasNext = page < totalPages - 1;
        return new PagedResponse<>(items, page, size, totalItems, totalPages, hasNext);
    }
}
