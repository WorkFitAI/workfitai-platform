package org.workfitai.cvservice.service.factory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.workfitai.cvservice.service.strategy.CvCreationStrategy;
import org.workfitai.cvservice.service.strategy.TemplateCvStrategy;
import org.workfitai.cvservice.service.strategy.UploadCvStrategy;

@Service
@RequiredArgsConstructor
public class CvCreationFactory {
    private final TemplateCvStrategy templateCvStrategy;
    private final UploadCvStrategy uploadCvStrategy;

    public CvCreationStrategy<?> getStrategy(String type) {
        return switch (type) {
            case "template" -> templateCvStrategy;
            case "upload" -> uploadCvStrategy;
            default -> throw new IllegalArgumentException("Unknown CV creation type: " + type);
        };
    }
}
