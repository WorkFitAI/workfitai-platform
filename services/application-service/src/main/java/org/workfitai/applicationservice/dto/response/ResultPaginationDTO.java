package org.workfitai.applicationservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic DTO for paginated query results.
 * 
 * Used for endpoints that return lists with pagination:
 * - GET /applications?page=0&size=20
 * 
 * Contains both the data items and pagination metadata
 * to help clients navigate through large result sets.
 * 
 * @param <T> Type of items in the result list
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated result wrapper with metadata")
public class ResultPaginationDTO<T> {

    /**
     * List of items for the current page.
     */
    @Schema(description = "List of items in the current page")
    private List<T> items;

    /**
     * Pagination metadata containing page info and navigation hints.
     */
    @Schema(description = "Pagination metadata")
    private Meta meta;

    /**
     * Pagination metadata nested class.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Pagination metadata")
    public static class Meta {

        /**
         * Current page number (0-indexed).
         */
        @Schema(description = "Current page number (0-indexed)", example = "0")
        private int page;

        /**
         * Number of items per page.
         */
        @Schema(description = "Number of items per page", example = "20")
        private int size;

        /**
         * Total number of items across all pages.
         */
        @Schema(description = "Total number of items", example = "150")
        private long totalElements;

        /**
         * Total number of pages available.
         */
        @Schema(description = "Total number of pages", example = "8")
        private int totalPages;

        /**
         * Whether this is the first page.
         */
        @Schema(description = "Is this the first page?", example = "true")
        private boolean first;

        /**
         * Whether this is the last page.
         */
        @Schema(description = "Is this the last page?", example = "false")
        private boolean last;

        /**
         * Whether there are more pages after this one.
         */
        @Schema(description = "Are there more pages?", example = "true")
        private boolean hasNext;

        /**
         * Whether there are pages before this one.
         */
        @Schema(description = "Are there previous pages?", example = "false")
        private boolean hasPrevious;
    }

    /**
     * Factory method to create pagination result from Spring Data Page.
     * 
     * @param items         The items for the current page
     * @param page          Current page number
     * @param size          Page size
     * @param totalElements Total count across all pages
     * @param totalPages    Total number of pages
     * @return Fully populated ResultPaginationDTO
     */
    public static <T> ResultPaginationDTO<T> of(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {

        return ResultPaginationDTO.<T>builder()
                .items(items)
                .meta(Meta.builder()
                        .page(page)
                        .size(size)
                        .totalElements(totalElements)
                        .totalPages(totalPages)
                        .first(page == 0)
                        .last(page >= totalPages - 1)
                        .hasNext(page < totalPages - 1)
                        .hasPrevious(page > 0)
                        .build())
                .build();
    }
}
