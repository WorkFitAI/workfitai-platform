package org.workfitai.jobservice.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.workfitai.jobservice.messaging.AuditEventPublisher;
import org.workfitai.jobservice.model.dto.kafka.AuditEvent;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditEventPublisher auditEventPublisher;
    @InjectMocks
    private AuditLogService auditLogService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void authenticateAsHr(String companyId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("companyId", companyId)
                .subject("hr1")
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_HR"))));
    }

    @Test
    void logAction_publishesSuccessEventWithRoleAndCompanyFromJwt() {
        authenticateAsHr("FPT#25");

        auditLogService.logAction("JOB", "job-1", "JOB_CREATED", "hr1",
                Map.of(), Map.of("title", "Java Dev"), Map.of());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.actorRole()).isEqualTo("HR");
        assertThat(event.companyId()).isEqualTo("FPT#25");
        assertThat(event.success()).isTrue();
        assertThat(event.errorMessage()).isNull();
        assertThat(event.action()).isEqualTo("JOB_CREATED");
    }

    @Test
    void logAction_defaultsRoleToSystem_whenUnauthenticated() {
        auditLogService.logAction("JOB", "job-1", "JOB_CREATED", "system", Map.of(), Map.of(), Map.of());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().actorRole()).isEqualTo("SYSTEM");
        assertThat(captor.getValue().companyId()).isNull();
    }

    @Test
    void logFailure_publishesFailureEventWithErrorMessage() {
        auditLogService.logFailure("JOB", "job-1", "JOB_UPDATE", "hr1", "validation failed", Map.of());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.success()).isFalse();
        assertThat(event.errorMessage()).isEqualTo("validation failed");
        assertThat(event.before()).isNull();
        assertThat(event.after()).isNull();
    }

    @Test
    void logAction_neverThrows_whenPublisherFails() {
        doThrow(new RuntimeException("kafka down")).when(auditEventPublisher).publish(any());

        auditLogService.logAction("JOB", "job-1", "JOB_CREATED", "hr1", Map.of(), Map.of(), Map.of());
    }

    @Test
    void logAction_fallsBackToNonRoleAuthority_whenNoRolePrefixedAuthorityPresent() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("svc", null,
                        List.of(new SimpleGrantedAuthority("job:create"), new SimpleGrantedAuthority("ADMIN"))));

        auditLogService.logAction("JOB", "job-1", "JOB_CREATED", "svc", Map.of(), Map.of(), Map.of());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().actorRole()).isEqualTo("ADMIN");
    }

    @Test
    void logAction_extractsClientIp_fromForwardedForHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditLogService.logAction("JOB", "job-1", "JOB_CREATED", "hr1", Map.of(), Map.of(), Map.of());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().actorIp()).isEqualTo("203.0.113.5");
    }

    @Test
    void logAction_extractsClientIp_fromRemoteAddr_whenForwardedHeaderMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditLogService.logAction("JOB", "job-1", "JOB_CREATED", "hr1", Map.of(), Map.of(), Map.of());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().actorIp()).isEqualTo("192.168.1.10");
    }
}
