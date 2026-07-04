/*
 * Function: Clock provider interface — abstracts time for testability
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.time;

import java.time.Clock;

/**
 * Provides the application {@link Clock}. Domain code depends on this interface
 * rather than calling {@code Instant.now()} directly, so tests can inject a
 * fixed clock for deterministic behaviour.
 */
public interface ClockProvider {

    Clock getClock();
}
