package org.workfitai.cvservice.service.impl;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.cvservice.errors.CVConflictException;
import org.workfitai.cvservice.errors.InvalidDataException;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.model.dto.request.ReqCvDTO;
import org.workfitai.cvservice.model.dto.response.ResCvDTO;
import org.workfitai.cvservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.cvservice.model.mapper.CVMapper;
import org.workfitai.cvservice.repository.CVRepository;
import org.workfitai.cvservice.service.iCVService;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.workfitai.cvservice.constant.ErrorConst.CV_CONFLICT_DATA;
import static org.workfitai.cvservice.constant.ErrorConst.CV_NOT_FOUND;

@Service
@AllArgsConstructor
public class CVService implements iCVService {

    private final CVRepository repository;
    private final MongoTemplate mongoTemplate;


    // ---------------- Create ----------------
    @Override
    public ResCvDTO create(@Valid ReqCvDTO req) {
        CV cv = CVMapper.INSTANCE.toEntity(req);
        cv.setBelongTo(UUID.randomUUID().toString()); // Chỉnh lại lấy từ Kafka sau!
        var saved = repository.save(cv);
        return CVMapper.INSTANCE.toResDTO(saved);
    }

    // ---------------- Read ----------------
    @Override
    public ResCvDTO getById(String cvId) {
        CV cv = repository.findById(cvId)
                .filter(CV::isExist) // chỉ lấy CV chưa xóa
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, CV_NOT_FOUND));
        return CVMapper.INSTANCE.toResDTO(cv);
    }

    @Override
    public ResultPaginationDTO<ResCvDTO> getByUser(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<CV> cvPage = repository.findByBelongToAndIsExistTrue(userId, pageable);

        Page<ResCvDTO> cvDTOPage = cvPage.map(CVMapper.INSTANCE::toResDTO);

        ResultPaginationDTO<ResCvDTO> rs = new ResultPaginationDTO<>();

        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
        mt.setPage(pageable.getPageNumber() + 1); // page hiện tại
        mt.setPageSize(pageable.getPageSize());
        mt.setPages(cvPage.getTotalPages());
        mt.setTotal(cvPage.getTotalElements());

        rs.setMeta(mt);
        rs.setResult(cvDTOPage.getContent());

        return rs;
    }

    @Override
    public ResultPaginationDTO<ResCvDTO> getByUserWithFilter(
            String userId,
            Map<String, Object> filters,
            int page,
            int size
    ) {
        Query query = new Query();

        // Filter cơ bản: userId + isExist
        addUserExistCriteria(query, userId);

        // Filter templateType (nếu có)
        applyTemplateTypeFilter(query, filters);

        // Filter các field trong sections
        applySectionsFilter(query, filters);

        // Count tổng
        long total = mongoTemplate.count(query, CV.class);

        // Pagination & sort
        query.with(PageRequest.of(page, size, Sort.by("createdAt").descending()));

        List<CV> cvs = mongoTemplate.find(query, CV.class);
        List<ResCvDTO> dtos = cvs.stream()
                .map(CVMapper.INSTANCE::toResDTO)
                .toList();

        // Build result
        ResultPaginationDTO<ResCvDTO> rs = new ResultPaginationDTO<>();
        rs.setMeta(buildMeta(total, page, size));
        rs.setResult(dtos);

        return rs;
    }

    // ====== helper methods ======
    private void addUserExistCriteria(Query query, String userId) {
        query.addCriteria(Criteria.where("belongTo").is(userId).and("isExist").is(true));
    }

    private void applyTemplateTypeFilter(Query query, Map<String, Object> filters) {
        Object templateTypeObj = filters.remove("templateType");
        if (templateTypeObj != null) {
            if (templateTypeObj instanceof String str) {
                query.addCriteria(Criteria.where("templateType").is(str));
            } else if (templateTypeObj instanceof Enum<?> e) {
                query.addCriteria(Criteria.where("templateType").is(e.name()));
            }
        }
    }

    private void applySectionsFilter(Query query, Map<String, Object> filters) {
        filters.forEach((k, v) -> {
            if (v instanceof Collection<?> coll && !coll.isEmpty()) {
                query.addCriteria(Criteria.where("sections." + k).in(coll));
            } else if (v instanceof String str) {
                query.addCriteria(Criteria.where("sections." + k).is(str));
            } else if (v instanceof Enum<?> e) {
                query.addCriteria(Criteria.where("sections." + k).is(e.name()));
            } else {
                query.addCriteria(Criteria.where("sections." + k).is(v));
            }
        });
    }

    private ResultPaginationDTO.Meta buildMeta(long total, int page, int size) {
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
        mt.setPage(page + 1);
        mt.setPageSize(size);
        mt.setPages((int) Math.ceil((double) total / size));
        mt.setTotal(total);
        return mt;
    }


    // ---------------- Update ----------------
    @Override
    public ResCvDTO update(String cvId, @Valid ReqCvDTO req) throws CVConflictException, InvalidDataException {
        CV cv = repository.findById(cvId)
                .orElseThrow(() -> new InvalidDataException(CV_NOT_FOUND));

        if (!cv.isExist()) {
            throw new CVConflictException(CV_CONFLICT_DATA);
        }

        // Map các field từ DTO vào entity hiện tại
        CVMapper.INSTANCE.updateFromDto(req, cv);

        CV saved = repository.save(cv);
        return CVMapper.INSTANCE.toResDTO(saved);
    }

    // ---------------- Delete (soft delete) ----------------
    @Override
    public void delete(String cvId) throws CVConflictException, InvalidDataException {
        CV cv = repository.findById(cvId)
                .orElseThrow(() -> new InvalidDataException(CV_NOT_FOUND));

        if (!cv.isExist()) {
            throw new CVConflictException(CV_CONFLICT_DATA);
        }

        cv.setExist(false);
        repository.save(cv);
    }
}
