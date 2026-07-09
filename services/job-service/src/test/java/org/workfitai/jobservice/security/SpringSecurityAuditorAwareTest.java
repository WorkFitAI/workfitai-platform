package org.workfitai.jobservice.security;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.workfitai.jobservice.config.Constants;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpringSecurityAuditorAwareTest {

  private final SpringSecurityAuditorAware auditorAware = new SpringSecurityAuditorAware();

  @Test
  void shouldReturnCurrentUserWhenAuthenticated() {
    try (MockedStatic<SecurityUtils> mockedStatic = Mockito.mockStatic(SecurityUtils.class)) {

      mockedStatic.when(SecurityUtils::getCurrentUserLogin)
          .thenReturn(Optional.of("john"));

      Optional<String> auditor = auditorAware.getCurrentAuditor();

      assertEquals(Optional.of("john"), auditor);
    }
  }

  @Test
  void shouldReturnSystemAccountWhenNoAuthenticatedUser() {
    try (MockedStatic<SecurityUtils> mockedStatic = Mockito.mockStatic(SecurityUtils.class)) {

      mockedStatic.when(SecurityUtils::getCurrentUserLogin)
          .thenReturn(Optional.empty());

      Optional<String> auditor = auditorAware.getCurrentAuditor();

      assertEquals(Optional.of(Constants.SYSTEM_ACCOUNT), auditor);
    }
  }
}