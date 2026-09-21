package com.metalcor.procurement.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One page of results")
public record PageResponse<T>(
        @Schema(description = "Items of the requested page") List<T> items,
        @Schema(description = "Page number, starting at 0") int page,
        @Schema(description = "Page size requested") int size,
        @Schema(description = "Total items across all pages") long totalElements,
        @Schema(description = "Total number of pages") int totalPages) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(items, page, size, totalElements, totalPages);
    }
}
