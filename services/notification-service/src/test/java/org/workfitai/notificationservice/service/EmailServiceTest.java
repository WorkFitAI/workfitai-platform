package org.workfitai.notificationservice.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;

import jakarta.mail.Session;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private TemplateService templateService;
    @Mock
    private UserPreferenceService userPreferenceService;

    private EmailService emailService;

    private void init() {
        emailService = new EmailService(mailSender, templateService, userPreferenceService);
        ReflectionTestUtils.setField(emailService, "mailUsername", "noreply@workfitai.com");
        ReflectionTestUtils.setField(emailService, "mailPassword", "secret");
        ReflectionTestUtils.setField(emailService, "mailFrom", "noreply@workfitai.com");
        ReflectionTestUtils.setField(emailService, "mailFromName", "WorkFitAI");
    }

    private MimeMessage realMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    private NotificationEvent.NotificationEventBuilder baseEvent() {
        return NotificationEvent.builder().recipientEmail("alice@test.com").subject("Hi").content("Hello");
    }

    @Test
    void sendEmail_returnsFalse_whenRecipientMissing() {
        init();
        NotificationEvent event = NotificationEvent.builder().recipientEmail(null).build();

        boolean result = emailService.sendEmail(event);

        assertThat(result).isFalse();
    }

    @Test
    void sendEmail_returnsFalse_whenMailCredentialsNotConfigured() {
        init();
        ReflectionTestUtils.setField(emailService, "mailUsername", "");
        NotificationEvent event = baseEvent().build();

        boolean result = emailService.sendEmail(event);

        assertThat(result).isFalse();
    }

    @Test
    void sendEmail_returnsFalse_whenUserPreferenceDisallows() {
        init();
        NotificationEvent event = baseEvent().build();
        when(userPreferenceService.shouldSendNotification(event)).thenReturn(false);

        boolean result = emailService.sendEmail(event);

        assertThat(result).isFalse();
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void sendEmail_sendsPlainContent_whenNoTemplateType() throws Exception {
        init();
        NotificationEvent event = baseEvent().templateType(null).build();
        when(userPreferenceService.shouldSendNotification(event)).thenReturn(true);
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());

        boolean result = emailService.sendEmail(event);

        assertThat(result).isTrue();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendEmail_usesTemplateHtml_whenTemplateRendersSuccessfully() throws Exception {
        init();
        NotificationEvent event = baseEvent().templateType("OTP_VERIFICATION").build();
        when(userPreferenceService.shouldSendNotification(event)).thenReturn(true);
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
        when(templateService.processTemplate(eq("otp-verification"), any())).thenReturn("<html>OTP</html>");

        boolean result = emailService.sendEmail(event);

        assertThat(result).isTrue();
        verify(templateService).processTemplate(eq("otp-verification"), any());
    }

    @Test
    void sendEmail_fallsBackToPlainContent_whenTemplateRendersNull() throws Exception {
        init();
        NotificationEvent event = baseEvent().templateType("password-reset").build();
        when(userPreferenceService.shouldSendNotification(event)).thenReturn(true);
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
        when(templateService.processTemplate(eq("password-reset"), any())).thenReturn(null);

        boolean result = emailService.sendEmail(event);

        assertThat(result).isTrue();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendEmail_usesDefaultSubject_whenSubjectNull() throws Exception {
        init();
        NotificationEvent event = baseEvent().subject(null).build();
        when(userPreferenceService.shouldSendNotification(event)).thenReturn(true);
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());

        boolean result = emailService.sendEmail(event);

        assertThat(result).isTrue();
    }

    @Test
    void sendEmail_returnsFalse_whenMailSenderThrows() {
        init();
        NotificationEvent event = baseEvent().build();
        when(userPreferenceService.shouldSendNotification(event)).thenReturn(true);
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("smtp down"));

        boolean result = emailService.sendEmail(event);

        assertThat(result).isFalse();
    }

    @Test
    void sendEmail_convertsUppercaseSnakeCaseTemplateType_toKebabCase() throws Exception {
        init();
        NotificationEvent event = baseEvent().templateType("CV_UPLOAD_SUCCESS").build();
        when(userPreferenceService.shouldSendNotification(event)).thenReturn(true);
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
        when(templateService.processTemplate(eq("cv-upload-success"), any())).thenReturn("<html>x</html>");

        emailService.sendEmail(event);

        verify(templateService).processTemplate(eq("cv-upload-success"), any());
    }
}
