package org.workfitai.cvservice.aspect;

import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.workfitai.cvservice.dto.kafka.AuditEvent;
import org.workfitai.cvservice.messaging.AuditEventPublisher;
import org.workfitai.cvservice.model.dto.response.ResCvDTO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock
    private AuditEventPublisher publisher;

    private AuditAspect aspect;

    private void init() {
        aspect = new AuditAspect(publisher);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void mockJwtAuth(String username, String role) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(username);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities, username));
    }

    @Test
    void logCvCreated_publishesEvent_whenResultIsResCvDTO() {
        init();
        mockJwtAuth("alice", "CANDIDATE");
        ResCvDTO dto = ResCvDTO.builder().cvId("cv-1").belongTo("alice").build();

        aspect.logCvCreated(dto);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("CV_CREATED");
        assertThat(captor.getValue().entityId()).isEqualTo("cv-1");
        assertThat(captor.getValue().actorUsername()).isEqualTo("alice");
        assertThat(captor.getValue().actorRole()).isEqualTo("CANDIDATE");
    }

    @Test
    void logCvCreated_doesNothing_whenResultIsNotResCvDTO() {
        init();

        aspect.logCvCreated("not-a-cv-dto");

        verify(publisher, org.mockito.Mockito.never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void logCvUpdated_publishesEvent_whenResultIsResCvDTO() {
        init();
        mockJwtAuth("alice", "CANDIDATE");
        ResCvDTO dto = ResCvDTO.builder().cvId("cv-1").belongTo("alice").build();

        aspect.logCvUpdated(dto);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("CV_UPDATED");
    }

    @Test
    void logCvDeleted_publishesEventWithCvIdFromJoinPointArgs() {
        init();
        mockJwtAuth("alice", "CANDIDATE");
        JoinPoint jp = mock(JoinPoint.class);
        when(jp.getArgs()).thenReturn(new Object[] { "cv-1" });

        aspect.logCvDeleted(jp);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("CV_DELETED");
        assertThat(captor.getValue().entityId()).isEqualTo("cv-1");
    }

    @Test
    void logCvDownloaded_publishesEventWithObjectNameFromJoinPointArgs() {
        init();
        mockJwtAuth("alice", "CANDIDATE");
        JoinPoint jp = mock(JoinPoint.class);
        when(jp.getArgs()).thenReturn(new Object[] { "file-1.pdf" });

        aspect.logCvDownloaded(jp);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("CV_DOWNLOADED");
        assertThat(captor.getValue().entityId()).isEqualTo("file-1.pdf");
    }

    @Test
    void publish_usesSystemActor_whenNoAuthenticationPresent() {
        init();
        JoinPoint jp = mock(JoinPoint.class);
        when(jp.getArgs()).thenReturn(new Object[] { "cv-1" });

        aspect.logCvDeleted(jp);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().actorUsername()).isEqualTo("SYSTEM");
        assertThat(captor.getValue().actorRole()).isEqualTo("SYSTEM");
    }

    @Test
    void publish_usesAuthenticationName_whenNotJwtToken() {
        init();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("bob", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        JoinPoint jp = mock(JoinPoint.class);
        when(jp.getArgs()).thenReturn(new Object[] { "cv-1" });

        aspect.logCvDeleted(jp);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().actorUsername()).isEqualTo("bob");
        assertThat(captor.getValue().actorRole()).isEqualTo("ADMIN");
    }

    @Test
    void publish_extractsClientIpFromXForwardedForHeader() {
        init();
        mockJwtAuth("alice", "CANDIDATE");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.5, 10.0.0.6");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        JoinPoint jp = mock(JoinPoint.class);
        when(jp.getArgs()).thenReturn(new Object[] { "cv-1" });

        aspect.logCvDeleted(jp);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().actorIp()).isEqualTo("10.0.0.5");
    }

    @Test
    void publish_fallsBackToRemoteAddr_whenNoForwardedHeader() {
        init();
        mockJwtAuth("alice", "CANDIDATE");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        JoinPoint jp = mock(JoinPoint.class);
        when(jp.getArgs()).thenReturn(new Object[] { "cv-1" });

        aspect.logCvDeleted(jp);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().actorIp()).isEqualTo("192.168.1.1");
    }

    @Test
    void publish_returnsNullIp_whenNoRequestContext() {
        init();
        mockJwtAuth("alice", "CANDIDATE");
        JoinPoint jp = mock(JoinPoint.class);
        when(jp.getArgs()).thenReturn(new Object[] { "cv-1" });

        aspect.logCvDeleted(jp);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().actorIp()).isNull();
    }

    @Test
    void publish_swallowsException_whenPublisherThrows() {
        init();
        mockJwtAuth("alice", "CANDIDATE");
        org.mockito.Mockito.doThrow(new RuntimeException("kafka down")).when(publisher).publish(org.mockito.ArgumentMatchers.any());
        JoinPoint jp = mock(JoinPoint.class);
        when(jp.getArgs()).thenReturn(new Object[] { "cv-1" });

        aspect.logCvDeleted(jp);
    }
}
