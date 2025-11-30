package org.workfitai.cvservice.service.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.model.dto.request.ReqCvUploadDTO;
import org.workfitai.cvservice.model.mapper.CVMapper;
import org.workfitai.cvservice.service.shared.FileService;

@Service
@RequiredArgsConstructor
public class UploadCvStrategy implements CvCreationStrategy<ReqCvUploadDTO> {

    private final FileService fileService;

    @Override
    public CV createCv(ReqCvUploadDTO dto) {
        try {
            String objectName = fileService.uploadCV(dto.getFile());

            String fileUrl = fileService.generateFileUrl(objectName);

            dto.setPdfUrl(fileUrl);
            dto.setObjectName(objectName); // Dùng cho việc download CV

            return CVMapper.INSTANCE.toEntityFromUpload(dto);

        } catch (Exception e) {
            throw new RuntimeException("Upload CV file failed", e);
        }
    }
}

