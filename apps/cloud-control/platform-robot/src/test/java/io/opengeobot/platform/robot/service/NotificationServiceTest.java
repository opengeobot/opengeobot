/*
 * Function: NotificationService unit tests — real delivery for in-app, webhook, email
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.robot.domain.AlarmEvent;
import io.opengeobot.platform.robot.domain.NotificationChannel;
import io.opengeobot.platform.robot.domain.NotificationLog;
import io.opengeobot.platform.robot.repository.NotificationChannelRepository;
import io.opengeobot.platform.robot.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link NotificationService}. Verifies that each
 * notification channel delivers (or fails) with the correct status —
 * success is never faked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock private NotificationChannelRepository channelRepository;
    @Mock private NotificationLogRepository logRepository;
    @Mock private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock private JavaMailSender mailSender;

    private NotificationService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(logRepository.insert(any(NotificationLog.class))).thenReturn(1);
    }

    private AlarmEvent createAlarmEvent() {
        AlarmEvent event = new AlarmEvent();
        event.setAlarmId("alm_001");
        event.setRuleId("alr_001");
        event.setSource("robot_registry");
        event.setSeverity("HIGH");
        event.setMessage("Robot has been offline for 5 minutes");
        event.setStatus("ACTIVE");
        event.setTriggeredAt(OffsetDateTime.now(ZoneOffset.UTC));
        event.setTraceId("trace-001");
        return event;
    }

    private NotificationChannel createChannel(String channelId, String name, String type,
                                               Map<String, Object> config, boolean enabled) {
        NotificationChannel channel = new NotificationChannel();
        channel.setChannelId(channelId);
        channel.setName(name);
        channel.setType(type);
        channel.setConfig(config);
        channel.setEnabled(enabled);
        return channel;
    }

    @Test
    void sendNotifications_inAppChannel_returnsSent() {
        NotificationChannel channel = createChannel(
                "nch_001", "in-app", "in-app", Map.of(), true);
        when(channelRepository.selectList(any())).thenReturn(List.of(channel));
        service = new NotificationService(channelRepository, logRepository, objectMapper,
                mailSenderProvider, "");

        service.sendNotifications(createAlarmEvent());

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).insert(logCaptor.capture());
        assertEquals("SENT", logCaptor.getValue().getStatus());
        assertNull(logCaptor.getValue().getErrorMessage());
    }

    @Test
    void sendNotifications_emailNotConfigured_returnsFailed() {
        NotificationChannel channel = createChannel(
                "nch_002", "email", "email", Map.of("to", "admin@example.com"), true);
        when(channelRepository.selectList(any())).thenReturn(List.of(channel));
        service = new NotificationService(channelRepository, logRepository, objectMapper,
                mailSenderProvider, "");

        service.sendNotifications(createAlarmEvent());

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).insert(logCaptor.capture());
        assertEquals("FAILED", logCaptor.getValue().getStatus());
        assertTrue(logCaptor.getValue().getErrorMessage().contains("Email not configured"));
    }

    @Test
    void sendNotifications_emailConfiguredButNoMailSender_returnsFailed() {
        NotificationChannel channel = createChannel(
                "nch_003", "email", "email", Map.of("to", "admin@example.com"), true);
        when(channelRepository.selectList(any())).thenReturn(List.of(channel));
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);
        service = new NotificationService(channelRepository, logRepository, objectMapper,
                mailSenderProvider, "smtp.example.com");

        service.sendNotifications(createAlarmEvent());

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).insert(logCaptor.capture());
        assertEquals("FAILED", logCaptor.getValue().getStatus());
        assertTrue(logCaptor.getValue().getErrorMessage().contains("JavaMailSender"));
    }

    @Test
    void sendNotifications_emailNoRecipientConfigured_returnsFailed() {
        NotificationChannel channel = createChannel(
                "nch_004", "email", "email", Map.of(), true);
        when(channelRepository.selectList(any())).thenReturn(List.of(channel));
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        service = new NotificationService(channelRepository, logRepository, objectMapper,
                mailSenderProvider, "smtp.example.com");

        service.sendNotifications(createAlarmEvent());

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).insert(logCaptor.capture());
        assertEquals("FAILED", logCaptor.getValue().getStatus());
        assertTrue(logCaptor.getValue().getErrorMessage().contains("no 'to' recipient"));
    }

    @Test
    void sendNotifications_unknownChannelType_returnsFailed() {
        NotificationChannel channel = createChannel(
                "nch_005", "sms", "sms", Map.of(), true);
        when(channelRepository.selectList(any())).thenReturn(List.of(channel));
        service = new NotificationService(channelRepository, logRepository, objectMapper,
                mailSenderProvider, "");

        service.sendNotifications(createAlarmEvent());

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).insert(logCaptor.capture());
        assertEquals("FAILED", logCaptor.getValue().getStatus());
        assertTrue(logCaptor.getValue().getErrorMessage().contains("Unknown notification channel"));
    }

    @Test
    void sendNotifications_webhookNoUrlConfigured_returnsFailed() {
        NotificationChannel channel = createChannel(
                "nch_006", "webhook", "webhook", Map.of(), true);
        when(channelRepository.selectList(any())).thenReturn(List.of(channel));
        service = new NotificationService(channelRepository, logRepository, objectMapper,
                mailSenderProvider, "");

        service.sendNotifications(createAlarmEvent());

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).insert(logCaptor.capture());
        assertEquals("FAILED", logCaptor.getValue().getStatus());
        assertTrue(logCaptor.getValue().getErrorMessage().contains("no URL configured"));
    }

    @Test
    void sendNotifications_webhookInvalidUrl_returnsFailed() {
        NotificationChannel channel = createChannel(
                "nch_007", "webhook", "webhook",
                Map.of("url", "http://localhost:1/nonexistent"), true);
        when(channelRepository.selectList(any())).thenReturn(List.of(channel));
        service = new NotificationService(channelRepository, logRepository, objectMapper,
                mailSenderProvider, "");

        service.sendNotifications(createAlarmEvent());

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).insert(logCaptor.capture());
        assertEquals("FAILED", logCaptor.getValue().getStatus());
    }

    @Test
    void sendNotifications_multipleChannels_continuesAfterFailure() {
        NotificationChannel inApp = createChannel(
                "nch_008", "in-app", "in-app", Map.of(), true);
        NotificationChannel email = createChannel(
                "nch_009", "email", "email", Map.of("to", "admin@example.com"), true);
        when(channelRepository.selectList(any())).thenReturn(List.of(inApp, email));
        service = new NotificationService(channelRepository, logRepository, objectMapper,
                mailSenderProvider, "");

        service.sendNotifications(createAlarmEvent());

        verify(logRepository, times(2)).insert(any(NotificationLog.class));
    }

    @Test
    void sendNotifications_emailConfigured_sendsEmail() {
        NotificationChannel channel = createChannel(
                "nch_010", "email", "email",
                Map.of("to", "admin@example.com", "subject_prefix", "[ALERT]"), true);
        when(channelRepository.selectList(any())).thenReturn(List.of(channel));
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(new jakarta.mail.internet.MimeMessage(
                jakarta.mail.Session.getInstance(new java.util.Properties())));
        service = new NotificationService(channelRepository, logRepository, objectMapper,
                mailSenderProvider, "smtp.example.com");

        service.sendNotifications(createAlarmEvent());

        verify(mailSender).send(any(jakarta.mail.internet.MimeMessage.class));
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).insert(logCaptor.capture());
        assertEquals("SENT", logCaptor.getValue().getStatus());
    }
}
