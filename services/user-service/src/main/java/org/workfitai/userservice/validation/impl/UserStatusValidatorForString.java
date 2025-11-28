package org.workfitai.userservice.validation.impl;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.validation.validator.ValidUserStatus;

public class UserStatusValidatorForString implements ConstraintValidator<ValidUserStatus, String> {
  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) return true;
    return EUserStatus.fromJsonSafe(value).isPresent();
  }
}