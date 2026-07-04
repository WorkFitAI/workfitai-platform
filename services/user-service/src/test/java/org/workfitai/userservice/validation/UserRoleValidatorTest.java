package org.workfitai.userservice.validation;

import org.junit.jupiter.api.Test;
import org.workfitai.userservice.validation.impl.UserRoleValidatorForEnum;
import org.workfitai.userservice.validation.impl.UserRoleValidatorForString;
import org.workfitai.userservice.enums.EUserRole;

import static org.assertj.core.api.Assertions.assertThat;

class UserRoleValidatorTest {

    private final UserRoleValidatorForString stringValidator = new UserRoleValidatorForString();
    private final UserRoleValidatorForEnum enumValidator = new UserRoleValidatorForEnum();

    // ---- String validator ----

    @Test
    void string_null_returnsTrue() {
        assertThat(stringValidator.isValid(null, null)).isTrue();
    }

    @Test
    void string_validRole_returnsTrue() {
        assertThat(stringValidator.isValid("CANDIDATE", null)).isTrue();
        assertThat(stringValidator.isValid("HR", null)).isTrue();
        assertThat(stringValidator.isValid("ADMIN", null)).isTrue();
    }

    @Test
    void string_invalidRole_returnsFalse() {
        assertThat(stringValidator.isValid("UNKNOWN_ROLE", null)).isFalse();
        assertThat(stringValidator.isValid("", null)).isFalse();
    }

    // ---- Enum validator ----

    @Test
    void enum_null_returnsTrue() {
        assertThat(enumValidator.isValid(null, null)).isTrue();
    }

    @Test
    void enum_validRole_returnsTrue() {
        assertThat(enumValidator.isValid(EUserRole.CANDIDATE, null)).isTrue();
        assertThat(enumValidator.isValid(EUserRole.ADMIN, null)).isTrue();
    }
}
