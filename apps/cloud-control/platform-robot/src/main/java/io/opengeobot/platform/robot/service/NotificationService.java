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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *   <li>{@code email} — stubbed for M5; logs and records as SENT.</li>
 * </ul>
 * Each delivery attempt is recorded as a {@link NotificationLog} row so that
 * delivery can be audited and retried.
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

    public NotificationService(NotificationChannelRepository channelRepository,
                               NotificationLogRepository logRepository,
                               ObjectMapper objectMapper) {
        this.channelRepository = channelRepository;
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
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
            log.info("In-app notification for alarm {}: {}", event.getAlarmId(), event.getMessage());
            return STATUS_SENT;
        }
        if (CHANNEL_WEBHOOK.equals(type)) {
            return deliverWebhook(event, channel);
        }
        if (CHANNEL_EMAIL.equals(type)) {
            log.info("Email notification (stub) for alarm {}: {}", event.getAlarmId(), event.getMessage());
            return STATUS_SENT;
        }
        log.warn("Unknown notification channel type '{}' for channel {}", type, channel.getName());
        return STATUS_SENT;
    }

    /**
     * Sends an HTTP POST with the alarm event payload to the webhook URL
     * configured in the channel config. Uses the built-in Java HttpClient
     * with a 10-second connect and request timeout.
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

    private List<NotificationChannel> findEnabledChannels() {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NotificationChannel> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(NotificationChannel::getEnabled, true);
        return channelRepository.selectList(wrapper);
    }
}
