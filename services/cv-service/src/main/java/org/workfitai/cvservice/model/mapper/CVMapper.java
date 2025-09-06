package org.workfitai.cvservice.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.model.dto.request.ReqCvDTO;
import org.workfitai.cvservice.model.dto.response.ResCvDTO;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Mapper
public interface CVMapper {
    CVMapper INSTANCE = Mappers.getMapper(CVMapper.class);

    // ---------------- Create / DTO → Entity ----------------
    CV toEntity(ReqCvDTO req);

    // ---------------- Update DTO → Entity hiện tại ----------------
    void updateFromDto(ReqCvDTO req, @MappingTarget CV cv);

    // ---------------- Entity → Response DTO ----------------
    ResCvDTO toResDTO(CV cv);

    // ---------------- Convert Instant → OffsetDateTime ----------------
    default OffsetDateTime map(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
    
    // ---------------- Optional: Map sections ----------------
    default Map<String, Object> mapSections(Map<String, Object> sections) {
        return sections == null ? Map.of() : sections;
    }
}