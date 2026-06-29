package org.workfitai.notificationservice.messaging;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.notificationservice.client.UserServiceClient;
import org.workfitai.notificationservice.dto.kafka.ApplicationCreatedEvent;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.model.Notification;
import org.workfitai.notificationservice.service.NotificationPersistenceService;
import org.workfitai.notificationservice.service.TemplateService;

import jakarta.mail.Session;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationEventConsumerTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private NotificationPersistenceService persistenceService;
    @Mock
    private TemplateService templateService;
    @Mock
    private UserServiceClient userServiceClient;

    private ApplicationEventConsumer consumer;

    private void init() {
        consumer = new ApplicationEventConsumer(mailSender, persistenceService, templateService, userServiceClient);
        ReflectionTestUtils.setField(consumer, "mailUsername", "noreply@workfitai.com");
        ReflectionTestUtils.setField(consumer, "mailPassword", "secret");
    }

    private MimeMessage realMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    private ApplicationCreatedEvent.ApplicationData.ApplicationDataBuilder baseDataBuilder() {
        return ApplicationCreatedEvent.ApplicationData.builder()
                .applicationId("app-1").username("candidate1").jobTitle("Engineer")
                .companyName("Acme").appliedAt(Instant.now()).hrUsername("hr1");
    }

    private ApplicationCreatedEvent.ApplicationData baseData() {
        return baseDataBuilder().build();
    }

    private ApplicationCreatedEvent eventWith(ApplicationCreatedEvent.ApplicationData data) {
        return ApplicationCreatedEvent.builder().eventId("evt-1").eventType("APPLICATION_CREATED").data(data).build();
    }

    @Test
    void handleApplicationCreated_doesNothing_whenEventNull() {
        init();

        consumer.handleApplicationCreated(null);

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void handleApplicationCreated_doesNothing_whenDataNull() {
        init();
        ApplicationCreatedEvent event = ApplicationCreatedEvent.builder().eventId("evt-1").data(null).build();

        consumer.handleApplicationCreated(event);

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void handleApplicationCreated_sendsBothEmails_whenEverythingSucceeds() {
        init();
        when(userServiceClient.getUserByUsername(anyString()))
                .thenReturn(Map.of("data", Map.of("email", "user@test.com")));
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
        when(templateService.processTemplate(anyString(), any())).thenReturn("<html>ok</html>");

        consumer.handleApplicationCreated(eventWith(baseData()));

        verify(mailSender, times(2)).send(any(MimeMessage.class));
        verify(persistenceService, times(2)).saveEmailLogForApplication(
                eq("app-1"), eq("user@test.com"), anyString(), eq(true), eq(null));
        verify(persistenceService, times(2)).createNotification(any(NotificationEvent.class));
    }

    @Test
    void handleApplicationCreated_skipsCandidateEmail_whenEmailNotFound() {
        init();
        when(userServiceClient.getUserByUsername("candidate1")).thenReturn(null);
        when(userServiceClient.getUserByUsername("hr1")).thenReturn(Map.of("data", Map.of("email", "hr@test.com")));
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());

        consumer.handleApplicationCreated(eventWith(baseData()));

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void handleApplicationCreated_skipsHrEmail_whenHrUsernameNull() {
        init();
        ApplicationCreatedEvent.ApplicationData data = baseDataBuilder().hrUsername(null).build();
        when(userServiceClient.getUserByUsername("candidate1"))
                .thenReturn(Map.of("data", Map.of("email", "user@test.com")));
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());

        consumer.handleApplicationCreated(eventWith(data));

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        verify(userServiceClient, never()).getUserByUsername("hr1");
    }

    @Test
    void handleApplicationCreated_skipsBothEmails_whenMailCredentialsNotConfigured() {
        init();
        ReflectionTestUtils.setField(consumer, "mailUsername", "");
        when(userServiceClient.getUserByUsername(anyString()))
                .thenReturn(Map.of("data", Map.of("email", "user@test.com")));

        consumer.handleApplicationCreated(eventWith(baseData()));

        verify(mailSender, never()).createMimeMessage();
        verify(persistenceService, never()).saveEmailLogForApplication(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    void handleApplicationCreated_usesFallbackHtml_whenTemplateRendersNull() {
        init();
        when(userServiceClient.getUserByUsername(anyString()))
                .thenReturn(Map.of("data", Map.of("email", "user@test.com")));
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
        when(templateService.processTemplate(anyString(), any())).thenReturn(null);

        consumer.handleApplicationCreated(eventWith(baseData()));

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void handleApplicationCreated_swallowsException_whenMailSenderThrows() {
        init();
        when(userServiceClient.getUserByUsername(anyString()))
                .thenReturn(Map.of("data", Map.of("email", "user@test.com")));
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("smtp down"));

        consumer.handleApplicationCreated(eventWith(baseData()));
        // no exception propagated; per-email try/catch swallows failures independently
    }

    @Test
    void handleApplicationCreated_swallowsException_whenUserServiceClientThrows() {
        init();
        when(userServiceClient.getUserByUsername(anyString())).thenThrow(new RuntimeException("user-service down"));

        consumer.handleApplicationCreated(eventWith(baseData()));

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void handleApplicationCreated_usesUsername_whenCandidateNameNull() {
        init();
        ApplicationCreatedEvent.ApplicationData data = baseDataBuilder().candidateName(null).build();
        when(userServiceClient.getUserByUsername(anyString()))
                .thenReturn(Map.of("data", Map.of("email", "user@test.com")));
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());

        consumer.handleApplicationCreated(eventWith(data));

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }
}
