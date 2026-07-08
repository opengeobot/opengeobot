/*
 * Function: Notification service — delivers alarm notifications to configured channels
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.robot.domain.AlarmEvent;
import io.opengeobot.platform.robot.domain.NotificationChannel;
import io.opengeobot.platform.robot.domain.NotificationLog;
import io.opengeobot.platform.robot.repository.NotificationChannelRepository;
import io.opengeobot.platform.robot.repository.NotificationLogRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Delivers alarm notifications to all enabled notification channels. Three
 * delivery types are supported:
 * <ul>
 *   <li>{@code in-app} — records a notification log entry in the database.</li>
 *   <li>{@code webhook} — sends an HTTP POST to the configured URL.</li>
 *   <li>{@code email} — sends an email via SMTP when configured; returns
 *       {@code FAILED} with a clear message when SMTP is not configured.</li>
 * </ul>
 * Each delivery attempt is recorded as a {@link NotificationLog} row so that
 * delivery can be audited and retried. Notification status always reflects
 * the actual send result — success is never faked.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED = "FAILED";
    private static final String CHANNEL_IN_APP = "in-app";
    private static final String CHANNEL_WEBHOOK = "webhook";
    private static final String CHANNEL_EMAIL = "email";

    private final NotificationChannelRepository channelRepository;
    private final NotificationLogRepository logRepository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailHost;

    public NotificationService(NotificationChannelRepository channelRepository,
                               NotificationLogRepository logRepository,
                               ObjectMapper objectMapper,
                               ObjectProvider<JavaMailSender> mailSenderProvider,
                               @Value("${spring.mail.host:}") String mailHost) {
        this.channelRepository = channelRepository;
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
        this.mailSenderProvider = mailSenderProvider;
        this.mailHost = mailHost;
    }

    /**
     * Sends a notification for the given alarm event to all enabled channels.
     * Failures in individual channels do not abort the remaining deliveries.
     */
    public void sendNotifications(AlarmEvent event) {
        List<NotificationChannel> channels = findEnabledChannels();
        for (NotificationChannel channel : channels) {
            sendToChannel(event, channel);
        }
    }

    private void sendToChannel(AlarmEvent event, NotificationChannel channel) {
        NotificationLog logEntry = new NotificationLog();
        logEntry.setAlarmId(event.getAlarmId());
        logEntry.setChannelId(channel.getChannelId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        try {
            String result = deliver(event, channel);
            logEntry.setStatus(result != null ? result : STATUS_SENT);
            logEntry.setSentAt(now);
            logEntry.setErrorMessage(null);
        } catch (Exception e) {
            log.warn("Notification delivery failed for alarm {} via channel {}: {}",
                    event.getAlarmId(), channel.getName(), e.getMessage());
            logEntry.setStatus(STATUS_FAILED);
            logEntry.setSentAt(now);
            logEntry.setErrorMessage(e.getMessage());
        }
        logRepository.insert(logEntry);
    }

    /**
     * Delivers the notification via the channel-specific mechanism. Returns
     * the delivery status string, or throws on failure.
     */
    private String deliver(AlarmEvent event, NotificationChannel channel) throws Exception {
        String type = channel.getType();
        if (CHANNEL_IN_APP.equals(type)) {
            return deliverInApp(event, channel);
        }
        if (CHANNEL_WEBHOOK.equals(type)) {
            return deliverWebhook(event, channel);
        }
        if (CHANNEL_EMAIL.equals(type)) {
            return deliverEmail(event, channel);
        }
        throw new IllegalStateException(
                "Unknown notification channel type '" + type + "' for channel '" + channel.getName() + "'");
    }

    /**
     * In-app notification: the notification log entry itself serves as the
     * in-app record. The alarm_id and channel_id are stored in the
     * {@code alarm.notification_log} table, making the notification visible
     * to any in-app notification consumer that queries this table.
     */
    private String deliverInApp(AlarmEvent event, NotificationChannel channel) {
        log.debug("In-app notification recorded for alarm {} via channel {}",
                event.getAlarmId(), channel.getName());
        return STATUS_SENT;
    }

    /**
     * Sends an HTTP POST with the alarm event payload to the webhook URL
     * configured in the channel config. Uses the built-in Java HttpClient
     * with a 5-second connect timeout and 10-second request timeout.
     */
    private String deliverWebhook(AlarmEvent event, NotificationChannel channel) throws Exception {
        Map<String, Object> config = channel.getConfig();
        if (config == null || !config.containsKey("url")) {
            throw new IllegalStateException("Webhook channel '" + channel.getName() + "' has no URL configured");
        }
        String url = String.valueOf(config.get("url"));
        String payload = objectMapper.writeValueAsString(Map.of(
                "alarm_id", event.getAlarmId(),
                "rule_id", event.getRuleId(),
                "source", event.getSource(),
                "severity", event.getSeverity(),
                "message", event.getMessage(),
                "triggered_at", event.getTriggeredAt() != null ? event.getTriggeredAt().toString() : ""
        ));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.debug("Webhook delivered to {} (status {})", url, response.statusCode());
            return STATUS_SENT;
        }
        throw new RuntimeException("Webhook returned HTTP " + response.statusCode());
    }

    /**
     * Sends an email notification via SMTP. The recipient address(es) and
     * optional subject prefix are read from the channel config. If SMTP is
     * not configured (no {@code spring.mail.host} property), the delivery
     * fails with a clear "Email not configured" message rather than faking
     * success.
     */
    private String deliverEmail(AlarmEvent event, NotificationChannel channel) throws Exception {
        if (mailHost == null || mailHost.isBlank()) {
            throw new IllegalStateException("Email not configured (spring.mail.host is not set)");
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException("Email not configured (JavaMailSender bean is not available)");
        }

        Map<String, Object> config = channel.getConfig();
        if (config == null || !config.containsKey("to")) {
            throw new IllegalStateException(
                    "Email channel '" + channel.getName() + "' has no 'to' recipient configured");
        }
        String to = String.valueOf(config.get("to"));
        String subjectPrefix = config.containsKey("subject_prefix")
                ? String.valueOf(config.get("subject_prefix")) : "[OpenGeoBot Alarm]";
        String subject = subjectPrefix + " " + event.getSeverity() + ": " + event.getSource();
        String body = buildEmailBody(event);

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, false);
        mailSender.send(mimeMessage);

        log.info("Email notification sent for alarm {} to {}", event.getAlarmId(), to);
        return STATUS_SENT;
    }

    private String buildEmailBody(AlarmEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Alarm Notification\n\n");
        sb.append("Alarm ID: ").append(event.getAlarmId()).append("\n");
        sb.append("Rule ID: ").append(event.getRuleId()).append("\n");
        sb.append("Source: ").append(event.getSource()).append("\n");
        sb.append("Severity: ").append(event.getSeverity()).append("\n");
        sb.append("Message: ").append(event.getMessage()).append("\n");
        sb.append("Triggered At: ").append(event.getTriggeredAt()).append("\n");
        if (event.getTraceId() != null) {
            sb.append("Trace ID: ").append(event.getTraceId()).append("\n");
        }
        return sb.toString();
    }

    private List<NotificationChannel> findEnabledChannels() {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NotificationChannel> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(NotificationChannel::getEnabled, true);
        return channelRepository.selectList(wrapper);
    }
}
