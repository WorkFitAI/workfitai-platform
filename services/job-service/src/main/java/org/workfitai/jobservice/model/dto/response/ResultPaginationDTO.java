package org.workfitai.jobservice.model.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResultPaginationDTO {
    private Meta meta;

    private Object result;

    @Getter
    @Setter
    @Builder
    public static class Meta {
        private int page;

        private int pageSize;

        private int pages;

        private long total;
    }
}
