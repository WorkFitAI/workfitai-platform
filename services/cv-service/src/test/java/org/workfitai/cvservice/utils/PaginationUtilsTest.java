package org.workfitai.cvservice.utils;

import org.junit.jupiter.api.Test;
import org.workfitai.cvservice.model.dto.response.ResultPaginationDTO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationUtilsTest {

    @Test
    void buildResult_convertsZeroIndexedPageToOneIndexed() {
        ResultPaginationDTO<String> result = PaginationUtils.buildResult(List.of("a"), 1L, 0, 10);

        assertThat(result.getMeta().getPage()).isEqualTo(1);
    }

    @Test
    void buildResult_computesPagesAsCeilingOfTotalOverSize() {
        ResultPaginationDTO<String> result = PaginationUtils.buildResult(List.of(), 11L, 1, 5);

        assertThat(result.getMeta().getPages()).isEqualTo(3);
        assertThat(result.getMeta().getPage()).isEqualTo(2);
        assertThat(result.getMeta().getPageSize()).isEqualTo(5);
        assertThat(result.getMeta().getTotal()).isEqualTo(11L);
    }

    @Test
    void buildResult_setsResultListAsProvided() {
        List<String> data = List.of("a", "b");

        ResultPaginationDTO<String> result = PaginationUtils.buildResult(data, 2L, 0, 10);

        assertThat(result.getResult()).isEqualTo(data);
    }
}
