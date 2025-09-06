package org.workfitai.cvservice.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.workfitai.cvservice.errors.CVConflictException;
import org.workfitai.cvservice.errors.InvalidDataException;
import org.workfitai.cvservice.model.dto.request.ReqCvDTO;
import org.workfitai.cvservice.model.dto.response.ResCvDTO;
import org.workfitai.cvservice.model.dto.response.RestResponse;
import org.workfitai.cvservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.cvservice.service.iCVService;
import org.workfitai.cvservice.utils.ApiMessage;

import java.util.Map;

import static org.workfitai.cvservice.constant.MessageConst.*;

@RestController
@RequestMapping()
@AllArgsConstructor
public class CVApi {
    private final iCVService service;

    // ---------------- Create ----------------
    @PostMapping
    @ApiMessage(CV_CREATED_SUCCESSFULLY)
    public RestResponse<ResCvDTO> createCV(@Valid @RequestBody ReqCvDTO cv) {
        ResCvDTO created = service.create(cv);
        return RestResponse.created(created);
    }

    // ---------------- Read by ID ----------------
    @GetMapping("/{cvId}")
    @ApiMessage(CV_FETCHED_SUCCESSFULLY)
    public RestResponse<ResCvDTO> getCV(@PathVariable String cvId) {
        ResCvDTO res = service.getById(cvId);
        return RestResponse.success(res);
    }

    // ---------------- Read by User ----------------
    @GetMapping
    @ApiMessage(CV_DETAIL_FETCHED_SUCCESSFULLY)
    public RestResponse<ResultPaginationDTO<ResCvDTO>> getCVsByUserWithFilter(
            @RequestParam Map<String, Object> allParams
    ) {
        // Bóc userId, page, size ra khỏi map
        String userId = (String) allParams.remove("userId");
        int page = allParams.containsKey("page") ? Integer.parseInt(allParams.remove("page").toString()) : 0;
        int size = allParams.containsKey("size") ? Integer.parseInt(allParams.remove("size").toString()) : 10;

        // Phần còn lại là filters
        Map<String, Object> filters = allParams;

        ResultPaginationDTO<ResCvDTO> res = service.getByUserWithFilter(userId, filters, page, size);
        return RestResponse.success(res);
    }

    // ---------------- Update ----------------
    @PutMapping("/{cvId}")
    @ApiMessage(CV_UPDATED_SUCCESSFULLY)
    public RestResponse<ResCvDTO> updateCV(
            @PathVariable String cvId,
            @Valid @RequestBody ReqCvDTO cv
    ) throws InvalidDataException, CVConflictException {
        ResCvDTO updated = null;
        updated = service.update(cvId, cv);
        return RestResponse.success(updated);
    }

    // ---------------- Delete (soft delete) ----------------
    @DeleteMapping("/{cvId}")
    @ApiMessage(CV_DELETED_SUCCESSFULLY)
    public RestResponse<Void> deleteCV(@PathVariable String cvId) throws InvalidDataException, CVConflictException {
        service.delete(cvId);
        return RestResponse.deleted();
    }
}
