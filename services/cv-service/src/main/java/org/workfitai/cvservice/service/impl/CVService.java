package org.workfitai.cvservice.service.impl;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.cvservice.constant.CVConst;
import org.workfitai.cvservice.constant.ErrorConst;
import org.workfitai.cvservice.errors.CVConflictException;
import org.workfitai.cvservice.errors.InvalidDataException;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.model.dto.request.ReqCvDTO;
import org.workfitai.cvservice.model.dto.request.ReqCvUploadDTO;
import org.workfitai.cvservice.model.dto.response.ResCvDTO;
import org.workfitai.cvservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.cvservice.model.mapper.CVMapper;
import org.workfitai.cvservice.repository.CVRepository;
import org.workfitai.cvservice.service.factory.CvCreationFactory;
import org.workfitai.cvservice.service.iCVService;
import org.workfitai.cvservice.service.shared.FileService;
import org.workfitai.cvservice.service.strategy.CvCreationStrategy;
import org.workfitai.cvservice.utils.CvQueryBuilder;
import org.workfitai.cvservice.utils.PaginationUtils;
import org.workfitai.cvservice.validation.FileValidator;

import java.io.InputStream;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class CVService implements iCVService {
    private final CVRepository repository;
    private final MongoTemplate mongoTemplate;
    private final CvCreationFactory cvCreationFactory;
    private final FileService fileService;


    // ---------------- CREATE ----------------
    @Override
    @Transactional
    public <T> ResCvDTO createCv(String type, T dto) throws InvalidDataException {
        validateUploadFile(type, dto);


        CvCreationStrategy<T> strategy = (CvCreationStrategy<T>) cvCreationFactory.getStrategy(type);
        CV cv = strategy.createCv(dto);


        cv.setBelongTo(getCurrentUsername());


        CV saved = repository.save(cv);
        return CVMapper.INSTANCE.toResCreateDTO(saved);
    }


    // ---------------- GET BY ID ----------------
    @Override
    public ResCvDTO getById(String cvId) {
        CV cv = repository.findById(cvId)
                .filter(CV::isExist)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorConst.CV_NOT_FOUND));


        return CVMapper.INSTANCE.toResDTO(cv);
    }


    @Override
    public ResultPaginationDTO<ResCvDTO> getByUser(String userId, int page, int size) {
        return null;
    }


    // ---------------- GET WITHOUT FILTER ----------------
    @Override
    public ResultPaginationDTO<ResCvDTO> getCVByBelongTo(String username, int page, int size) {
        return getCVByBelongToWithFilter(username, Map.of(), page, size);
    }


    // ---------------- GET WITH FILTER ----------------
    @Override
    public ResultPaginationDTO<ResCvDTO> getCVByBelongToWithFilter(
            String username,
            Map<String, Object> filters,
            int page,
            int size
    ) {


        Query query = CvQueryBuilder.build(username, filters);


        long total = mongoTemplate.count(query, CV.class);


        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        query.with(pageable);


        List<ResCvDTO> results = mongoTemplate.find(query, CV.class)
                .stream()
                .map(CVMapper.INSTANCE::toResDTO)
                .toList();


        return PaginationUtils.buildResult(results, total, page, size);
    }


    // ---------------- UPDATE ----------------
    @Override
    public ResCvDTO update(String cvId, @Valid ReqCvDTO req) throws CVConflictException, InvalidDataException {
        CV cv = repository.findById(cvId)
                .orElseThrow(() -> new InvalidDataException(ErrorConst.CV_NOT_FOUND, HttpStatus.NOT_FOUND));


        if (!cv.isExist()) {
            throw new CVConflictException(ErrorConst.CV_CONFLICT_DATA);
        }


        CVMapper.INSTANCE.updateFromDto(req, cv);
        return CVMapper.INSTANCE.toResDTO(repository.save(cv));
    }


    // ---------------- SOFT DELETE ----------------
    @Override
    public void delete(String cvId) throws CVConflictException, InvalidDataException {
        CV cv = repository.findById(cvId)
                .orElseThrow(() -> new InvalidDataException(ErrorConst.CV_NOT_FOUND, HttpStatus.NOT_FOUND));


        if (!cv.isExist()) {
            throw new CVConflictException(ErrorConst.CV_CONFLICT_DATA);
        }


        cv.setExist(false);
        repository.save(cv);
    }


    @Override
    public ResultPaginationDTO<ResCvDTO> getByUserWithFilter(String userId, Map<String, Object> filters, int page, int size) {
        return null;
    }


    // ---------------- DOWNLOAD ----------------
    @Override
    public InputStream downloadCV(String objectName) {
        if (!objectName.matches(CVConst.PDF_FILE_PATTERN)) {
            throw new InvalidDataException(ErrorConst.CV_INVALID_FILE, HttpStatus.BAD_REQUEST);
        }


        try {
            return fileService.downloadCV(objectName);
        } catch (Exception e) {
            throw new InvalidDataException("Cannot read file: " + objectName, HttpStatus.BAD_REQUEST);
        }
    }


    // ---------------- PRIVATE UTILS ----------------
    private <T> void validateUploadFile(String type, T dto) {
        if ("upload".equalsIgnoreCase(type) && dto instanceof ReqCvUploadDTO upload) {
            FileValidator.validate(upload.getFile());
        }
    }


    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return ((Jwt) authentication.getPrincipal()).getClaimAsString("sub");
    }
}

