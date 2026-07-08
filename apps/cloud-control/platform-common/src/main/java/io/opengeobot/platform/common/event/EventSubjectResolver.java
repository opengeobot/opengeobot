/*
 * Function: Event subject resolver — maps outbox events to NATS subjects
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.common.event;

import org.springframework.stereotype.Component;

/**
 * Resolves the NATS subject for a given {@link OutboxEvent}. The default
 * strategy maps events to subjects following the pattern
 * {@code opengeobot.events.{aggregate_type}.{event_type}}, where dots in the
 * event type are replaced with underscores so that NATS subject tokens are
 * preserved.
 * <p>
 * For example, an event with {@code aggregateType=mission} and
 * {@code eventType=mission.created.v1} is published to
 * {@code opengeobot.events.mission.mission_created_v1}.
 */
@Component
public class EventSubjectResolver {

    private static final String SUBJECT_PREFIX = "opengeobot.events.";

    /**
     * Resolves the NATS subject for the given outbox event.
     *
     * @param event the outbox event (must not be {@code null})
     * @return the NATS subject string
     */
    public String resolveSubject(OutboxEvent event) {
        String aggregateType = sanitize(event.aggregateType());
        String eventType = sanitize(event.eventType());
        return SUBJECT_PREFIX + aggregateType + "." + eventType;
    }

    /**
     * Sanitises a token for use in a NATS subject: replaces dots with
     * underscores and trims whitespace.
     */
    private static String sanitize(String token) {
        if (token == null || token.isBlank()) {
            return "unknown";
        }
        return token.trim().replace('.', '_');
    }
}
