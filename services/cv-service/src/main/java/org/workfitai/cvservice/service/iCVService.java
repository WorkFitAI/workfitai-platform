package org.workfitai.cvservice.service;


import jakarta.validation.Valid;
import org.workfitai.cvservice.errors.CVConflictException;
import org.workfitai.cvservice.errors.InvalidDataException;
import org.workfitai.cvservice.model.dto.request.ReqCvDTO;
import org.workfitai.cvservice.model.dto.response.ResCvDTO;
import org.workfitai.cvservice.model.dto.response.ResultPaginationDTO;

import java.io.InputStream;
import java.util.Map;


public interface iCVService {


    <T> ResCvDTO createCv(String type, T dto) throws InvalidDataException;


    ResCvDTO getById(String cvId);


    ResultPaginationDTO<ResCvDTO> getCVByBelongTo(String username, int page, int size);


    ResCvDTO update(String cvId, @Valid ReqCvDTO req) throws CVConflictException, InvalidDataException;


    void delete(String cvId) throws CVConflictException, InvalidDataException;


    ResultPaginationDTO<ResCvDTO> getCVByBelongToWithFilter(
            String username,
            Map<String, Object> filters,
            int page,
            int size
    );


    InputStream downloadCV(String objectName) throws Exception;
}

