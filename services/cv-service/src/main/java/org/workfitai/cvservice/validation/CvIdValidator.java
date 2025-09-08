package org.workfitai.cvservice.validation;


import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.workfitai.cvservice.repository.CVRepository;

@Component
@AllArgsConstructor
public class CvIdValidator implements ConstraintValidator<ValidCvId, String> {

    private CVRepository cvRepository;

    @Override
    public boolean isValid(String cvId, ConstraintValidatorContext context) {
        if (cvId == null) return false;
        return !cvRepository.existsById(cvId);
    }
}