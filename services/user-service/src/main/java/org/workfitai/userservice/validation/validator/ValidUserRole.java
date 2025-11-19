package org.workfitai.userservice.validation.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.workfitai.userservice.validation.impl.UserRoleValidatorForEnum;
import org.workfitai.userservice.validation.impl.UserRoleValidatorForString;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = {UserRoleValidatorForString.class, UserRoleValidatorForEnum.class})
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidUserRole {
  String message() default "Invalid role, must be one of: Candidate, HR, Admin";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
