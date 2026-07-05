/*
 * Function: Actor and trace context resolver — pulls current actor/trace from the request context
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.web;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Resolves the current actor identifier and trace id for audit and ownership
 * fields. The actor is sourced from a request-scoped holder that the platform
 * security layer populates after authentication; until IAM integration is in
 * place the holder is empty and a stable {@code system} actor is returned so
 * audit rows remain populated. The trace id is sourced from the SLF4J MDC
 * populated by the platform tracing filter.
 */
@Component
public class ActorResolver {

    private static final String DEFAULT_ACTOR = "system";
    private static final String TRACE_ID_KEY = "traceId";

    private final ThreadLocal<String> currentActorHolder = new ThreadLocal<>();

    /**
     * Sets the actor for the current request. Called by the security layer
     * after authentication; safe to leave unset, in which case {@link #currentActor()}
     * returns the default actor.
     */
    public void setCurrentActor(String actor) {
        currentActorHolder.set(actor);
    }

    public void clear() {
        currentActorHolder.remove();
    }

    public String currentActor() {
        String actor = currentActorHolder.get();
        if (actor != null && !actor.isBlank()) {
            return actor;
        }
        return DEFAULT_ACTOR;
    }

    public String currentTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return (traceId == null || traceId.isBlank()) ? null : traceId;
    }
}
