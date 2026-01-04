package org.workfitai.userservice.validation.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.workfitai.userservice.validation.impl.UserStatusValidatorForEnum;
import org.workfitai.userservice.validation.impl.UserStatusValidatorForString;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = {UserStatusValidatorForString.class, UserStatusValidatorForEnum.class})
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidUserStatus {
  String message() default "Invalid status, must be one of: ACTIVE, PENDING, SUSPENDED, DEACTIVATED, DELETED";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
