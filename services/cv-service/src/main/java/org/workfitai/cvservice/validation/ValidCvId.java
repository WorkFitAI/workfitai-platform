package org.workfitai.cvservice.validation;


import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = CvIdValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCvId {
    String message() default "cvId is invalid or already exists";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}