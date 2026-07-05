/*
 * Function: Actor and trace context resolver for the robot module
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.web;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Resolves the current actor identifier and trace id for audit and ownership
 * fields in the robot skill domain. Mirrors the governance ActorResolver so
 * that the robot module remains self-contained without a cross-module
 * dependency on platform-governance.
 */
@Component("robotActorResolver")
public class ActorResolver {

    private static final String DEFAULT_ACTOR = "system";
    private static final String TRACE_ID_KEY = "traceId";

    /**
     * Sets the actor for the current request. Called by the security layer
     * after authentication; safe to leave unset, in which case
     * {@link #currentActor()} returns the default actor.
     */
    public void setCurrentActor(String actor) {
        RequestContextHolder.setActor(actor);
    }

    public void clear() {
        RequestContextHolder.clear();
    }

    public String currentActor() {
        String actor = RequestContextHolder.getActor();
        if (actor != null && !actor.isBlank()) {
            return actor;
        }
        return DEFAULT_ACTOR;
    }

    public String currentTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return (traceId == null || traceId.isBlank()) ? null : traceId;
    }

    /**
     * Simple thread-local holder for the current request actor. Populated by
     * the security layer; cleared at request end.
     */
    private static final class RequestContextHolder {

        private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();

        static void setActor(String actor) {
            ACTOR.set(actor);
        }

        static String getActor() {
            return ACTOR.get();
        }

        static void clear() {
            ACTOR.remove();
        }
    }
}
