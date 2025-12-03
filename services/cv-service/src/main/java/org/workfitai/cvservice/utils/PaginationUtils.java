package org.workfitai.cvservice.utils;


import org.workfitai.cvservice.model.dto.response.ResultPaginationDTO;

import java.util.List;


public class PaginationUtils {


    private PaginationUtils() {
    }


    public static <T> ResultPaginationDTO<T> buildResult(
            List<T> data,
            long total,
            int page,
            int size
    ) {
        ResultPaginationDTO<T> result = new ResultPaginationDTO<>();


        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(page + 1);
        meta.setPageSize(size);
        meta.setPages((int) Math.ceil((double) total / size));
        meta.setTotal(total);


        result.setMeta(meta);
        result.setResult(data);


        return result;
    }
}

