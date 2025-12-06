package org.workfitai.cvservice.service.strategy;

import org.workfitai.cvservice.model.CV;

public interface CvCreationStrategy<T> {
    CV createCv(T dto);
}
