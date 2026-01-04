package org.workfitai.userservice.validation.impl;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.validation.validator.ValidUserRole;

public class UserRoleValidatorForEnum implements ConstraintValidator<ValidUserRole, EUserRole> {
  @Override
  public boolean isValid(EUserRole value, ConstraintValidatorContext context) {
    if (value == null) return true;
    EUserRole.valueOf(value.name());
    return true;
  }
}
