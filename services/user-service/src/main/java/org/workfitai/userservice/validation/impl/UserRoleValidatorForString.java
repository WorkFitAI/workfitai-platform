package org.workfitai.userservice.validation.impl;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.validation.validator.ValidUserRole;

public class UserRoleValidatorForString implements ConstraintValidator<ValidUserRole, String> {
  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) return true;
    return EUserRole.fromJsonSafe(value).isPresent();
  }
}