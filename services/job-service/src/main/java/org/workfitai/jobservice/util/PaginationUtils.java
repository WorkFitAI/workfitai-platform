package org.workfitai.jobservice.util;

import org.springframework.data.domain.Page;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;

import java.util.function.Function;

public class PaginationUtils {

    private PaginationUtils() {
    }

    /**
     * Chuyển Page<Entity> sang ResultPaginationDTO với builder pattern
     *
     * @param pageData Page<Entity> từ repository
     * @param mapper   Function map từ Entity sang DTO
     * @param <E>      Entity
     * @param <D>      DTO
     * @return ResultPaginationDTO chứa result và meta
     */
    public static <E, D> ResultPaginationDTO toResultPaginationDTO(Page<E> pageData, Function<E, D> mapper) {
        Page<D> pageDto = pageData.map(mapper);

        ResultPaginationDTO.Meta meta = ResultPaginationDTO.Meta.builder()
                .page(pageData.getNumber() + 1)
                .pageSize(pageData.getSize())
                .pages(pageData.getTotalPages())
                .total(pageData.getTotalElements())
                .build();

        return ResultPaginationDTO.builder()
                .meta(meta)
                .result(pageDto.getContent())
                .build();
    }

    /**
     * Nếu muốn, overload cho List + Pageable + tổng số phần tử
     */
    public static <E, D> ResultPaginationDTO toResultPaginationDTO(
            java.util.List<E> list,
            org.springframework.data.domain.Pageable pageable,
            Function<E, D> mapper,
            long totalElements
    ) {
        java.util.List<D> dtoList = list.stream().map(mapper).toList();

        ResultPaginationDTO.Meta meta = ResultPaginationDTO.Meta.builder()
                .page(pageable.getPageNumber() + 1)
                .pageSize(pageable.getPageSize())
                .pages((int) Math.ceil((double) totalElements / pageable.getPageSize()))
                .total(totalElements)
                .build();

        return ResultPaginationDTO.builder()
                .meta(meta)
                .result(dtoList)
                .build();
    }
}

