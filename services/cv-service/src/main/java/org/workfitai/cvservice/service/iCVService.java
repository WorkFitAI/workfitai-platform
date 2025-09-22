package org.workfitai.cvservice.service;

import jakarta.validation.Valid;
import org.workfitai.cvservice.errors.CVConflictException;
import org.workfitai.cvservice.errors.InvalidDataException;
import org.workfitai.cvservice.model.dto.request.ReqCvDTO;
import org.workfitai.cvservice.model.dto.response.ResCvDTO;
import org.workfitai.cvservice.model.dto.response.ResultPaginationDTO;

import java.util.Map;

public interface iCVService {

    // ---------------- Create ----------------
    ResCvDTO create(@Valid ReqCvDTO req);

    // ---------------- Read ----------------
    ResCvDTO getById(String cvId);

    public ResultPaginationDTO<ResCvDTO> getByUser(String userId, int page, int size);

    // ---------------- Update ----------------
    ResCvDTO update(String cvId, @Valid ReqCvDTO req) throws CVConflictException, InvalidDataException;

    // ---------------- Delete (soft delete) ----------------
    void delete(String cvId) throws CVConflictException, InvalidDataException;

    ResultPaginationDTO<ResCvDTO> getByUserWithFilter(
            String userId,
            Map<String, Object> filters,
            int page,
            int size
    );
}
