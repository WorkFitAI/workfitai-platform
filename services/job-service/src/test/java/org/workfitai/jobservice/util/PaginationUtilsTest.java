package org.workfitai.jobservice.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationUtilsTest {

    @Test
    void toResultPaginationDTO_fromPage_mapsContentAndMeta() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 5);

        ResultPaginationDTO result = PaginationUtils.toResultPaginationDTO(page, s -> s.toUpperCase());

        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getPageSize()).isEqualTo(2);
        assertThat(result.getMeta().getTotal()).isEqualTo(5);
        assertThat((List<String>) result.getResult()).containsExactly("A", "B");
    }

    @Test
    void toResultPaginationDTO_fromListAndPageable_mapsContentAndMeta() {
        List<String> list = List.of("a", "b", "c");

        ResultPaginationDTO result = PaginationUtils.toResultPaginationDTO(
                list, PageRequest.of(0, 3), s -> s.toUpperCase(), 9L);

        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getPageSize()).isEqualTo(3);
        assertThat(result.getMeta().getPages()).isEqualTo(3);
        assertThat(result.getMeta().getTotal()).isEqualTo(9L);
        assertThat((List<String>) result.getResult()).containsExactly("A", "B", "C");
    }
}
