package org.workfitai.cvservice.service.strategy;

import org.springframework.stereotype.Service;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.model.dto.request.ReqCvTemplateDTO;
import org.workfitai.cvservice.model.mapper.CVMapper;

@Service
public class TemplateCvStrategy implements CvCreationStrategy<ReqCvTemplateDTO> {

    @Override
    public CV createCv(ReqCvTemplateDTO dto) {
        return CVMapper.INSTANCE.toEntityFromTemplate(dto);
    }
}
