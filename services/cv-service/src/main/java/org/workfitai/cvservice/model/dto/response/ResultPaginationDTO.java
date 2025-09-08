package org.workfitai.cvservice.model.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResultPaginationDTO<T> {
    private Meta meta;

    private List<T> result;

    @Getter
    @Setter
    public static class Meta {
        private int page;

        private int pageSize;

        private int pages;

        private long total;
    }
}