/*
 * Function: Outbox Relay — polls unpublished events and publishes to NATS JetStream
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.common.event;

import io.nats.client.JetStream;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Polls the transactional outbox for unpublished events and publishes them to
 * NATS JetStream. On successful publish, the event is marked as published. On
 * failure, the retry count is incremented and the next retry time is set with
 * exponential backoff so that the event is skipped on subsequent cycles until
 * the backoff expires.
 * <p>
 * The relay never crashes: all exceptions are caught per-event and per-cycle,
 * ensuring that a single failing event does not block the rest of the batch.
 * When NATS is unavailable, the relay skips the cycle entirely and leaves all
 * events unpublished for the next attempt.
 * <p>
 * Only instantiated when {@code opengeobot.nats.enabled=true} (the default).
 */
@Component
@ConditionalOnProperty(prefix = "opengeobot.nats", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outboxRepository;
    private final NatsConnectionManager connectionManager;
    private final EventSubjectResolver subjectResolver;
    private final NatsProperties properties;

    public OutboxRelay(OutboxRepository outboxRepository,
                       NatsConnectionManager connectionManager,
                       EventSubjectResolver subjectResolver,
                       NatsProperties properties) {
        this.outboxRepository = outboxRepository;
        this.connectionManager = connectionManager;
        this.subjectResolver = subjectResolver;
        this.properties = properties;
    }

    /**
     * Scheduled relay cycle. Runs at a configurable fixed delay (default 5 s).
     * The annotation uses a SpEL placeholder so that the interval can be tuned
     * via {@code opengeobot.nats.outbox.relay-interval-ms}.
     */
    @Scheduled(fixedDelayString = "${opengeobot.nats.outbox.relay-interval-ms:5000}")
    public void relayEvents() {
        try {
            if (!connectionManager.isConnected()) {
                boolean connected = connectionManager.tryConnect();
                if (!connected) {
                    log.debug("NATS unavailable, skipping outbox relay cycle");
                    return;
                }
            }

            int batchSize = properties.getOutbox().getBatchSize();
            List<OutboxEvent> events = outboxRepository.findUnpublishedForRelay(batchSize);
            if (events.isEmpty()) {
                return;
            }

            log.debug("Relaying {} unpublished outbox events", events.size());

            int published = 0;
            int failed = 0;
            for (OutboxEvent event : events) {
                if (publishSingleEvent(event)) {
                    published++;
                } else {
                    failed++;
                }
            }

            if (published > 0 || failed > 0) {
                log.info("Outbox relay cycle complete: published={}, failed={}, total={}",
                        published, failed, events.size());
            }
        } catch (Exception e) {
            log.error("Outbox relay cycle failed unexpectedly", e);
        }
    }

    /**
     * Publishes a single outbox event to NATS JetStream. Returns {@code true}
     * on success (event marked as published), {@code false} on failure (retry
     * count incremented with exponential backoff).
     */
    private boolean publishSingleEvent(OutboxEvent event) {
        String traceId = event.traceId() != null ? event.traceId() : "unknown";
        MDC.put("trace_id", traceId);
        try {
            String subject = subjectResolver.resolveSubject(event);
            byte[] data = event.payload().getBytes(StandardCharsets.UTF_8);

            JetStream js = connectionManager.getJetStream();
            if (js == null) {
                log.warn("JetStream not available, skipping event id={}", event.eventId());
                markFailed(event);
                return false;
            }

            PublishOptions options = PublishOptions.builder()
                    .messageId(event.eventId())
                    .build();

            PublishAck ack = js.publish(subject, data, options);
            outboxRepository.markPublished(event.id());
            log.debug("Published outbox event id={} type={} subject={} seq={}",
                    event.eventId(), event.eventType(), subject, ack.getSeqno());
            return true;
        } catch (Exception e) {
            log.warn("Failed to publish outbox event id={} type={}: {}",
                    event.eventId(), event.eventType(), e.getMessage());
            markFailed(event);
            return false;
        } finally {
            MDC.remove("trace_id");
        }
    }

    /**
     * Marks an event as failed with exponential backoff. The backoff is
     * {@code retryBackoffMs * 2^min(retryCount, 8)}, capped at 256× the base
     * backoff. Events exceeding {@code maxRetryCount} are still retried but
     * at the maximum backoff interval.
     */
    private void markFailed(OutboxEvent event) {
        try {
            int retryCount = event.retryCount();
            long baseBackoff = properties.getOutbox().getRetryBackoffMs();
            long multiplier = 1L << Math.min(retryCount, 8);
            long backoffMs = baseBackoff * multiplier;
            OffsetDateTime nextRetryAt = OffsetDateTime.now(ZoneOffset.UTC)
                    .plusNanos(backoffMs * 1_000_000L);
            outboxRepository.markPublishFailed(event.id(), nextRetryAt);

            if (retryCount + 1 >= properties.getOutbox().getMaxRetryCount()) {
                log.error("Outbox event id={} exceeded max retry count ({}), will continue retrying at max backoff",
                        event.eventId(), properties.getOutbox().getMaxRetryCount());
            }
        } catch (Exception e) {
            log.error("Failed to mark outbox event id={} as publish-failed: {}",
                    event.eventId(), e.getMessage());
        }
    }
}
