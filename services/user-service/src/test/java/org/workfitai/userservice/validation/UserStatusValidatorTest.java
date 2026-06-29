package org.workfitai.userservice.validation;

import org.junit.jupiter.api.Test;
import org.workfitai.userservice.validation.impl.UserStatusValidatorForEnum;
import org.workfitai.userservice.validation.impl.UserStatusValidatorForString;
import org.workfitai.userservice.enums.EUserStatus;

import static org.assertj.core.api.Assertions.assertThat;

class UserStatusValidatorTest {

    private final UserStatusValidatorForString stringValidator = new UserStatusValidatorForString();
    private final UserStatusValidatorForEnum enumValidator = new UserStatusValidatorForEnum();

    // ---- String validator ----

    @Test
    void string_null_returnsTrue() {
        assertThat(stringValidator.isValid(null, null)).isTrue();
    }

    @Test
    void string_validStatus_returnsTrue() {
        assertThat(stringValidator.isValid("ACTIVE", null)).isTrue();
        assertThat(stringValidator.isValid("PENDING", null)).isTrue();
        assertThat(stringValidator.isValid("SUSPENDED", null)).isTrue();
        assertThat(stringValidator.isValid("DEACTIVATED", null)).isTrue();
        assertThat(stringValidator.isValid("DELETED", null)).isTrue();
    }

    @Test
    void string_invalidStatus_returnsFalse() {
        assertThat(stringValidator.isValid("NOT_A_STATUS", null)).isFalse();
        assertThat(stringValidator.isValid("BLOCKED", null)).isFalse();
        assertThat(stringValidator.isValid("", null)).isFalse();
    }

    // ---- Enum validator ----

    @Test
    void enum_null_returnsTrue() {
        assertThat(enumValidator.isValid(null, null)).isTrue();
    }

    @Test
    void enum_validStatus_returnsTrue() {
        assertThat(enumValidator.isValid(EUserStatus.ACTIVE, null)).isTrue();
        assertThat(enumValidator.isValid(EUserStatus.PENDING, null)).isTrue();
    }
}
