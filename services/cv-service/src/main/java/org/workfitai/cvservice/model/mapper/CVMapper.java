package org.workfitai.cvservice.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.model.dto.request.ReqCvDTO;
import org.workfitai.cvservice.model.dto.request.ReqCvTemplateDTO;
import org.workfitai.cvservice.model.dto.request.ReqCvUploadDTO;
import org.workfitai.cvservice.model.dto.response.ResCvDTO;

import java.util.Map;

@Mapper
public interface CVMapper {
    CVMapper INSTANCE = Mappers.getMapper(CVMapper.class);

    // ---------------- Create / DTO → Entity ----------------
    CV toEntityFromTemplate(ReqCvTemplateDTO req);

    CV toEntityFromUpload(ReqCvUploadDTO req);

    // ---------------- Update DTO → Entity hiện tại ----------------
    void updateFromDto(ReqCvDTO req, @MappingTarget CV cv);

    // ---------------- Entity → Response DTO ----------------
    ResCvDTO toResDTO(CV cv);

    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "isExist", constant = "true")
    ResCvDTO toResCreateDTO(CV cv);

    // ---------------- Optional: Map sections ----------------
    default Map<String, Object> mapSections(Map<String, Object> sections) {
        return sections == null ? Map.of() : sections;
    }
}