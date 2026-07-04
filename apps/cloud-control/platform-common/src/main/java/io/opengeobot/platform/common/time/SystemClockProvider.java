/*
 * Function: System UTC clock provider — production default
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.time;

import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Production {@link ClockProvider} backed by {@link Clock#systemUTC()}.
 */
@Component
public final class SystemClockProvider implements ClockProvider {

    @Override
    public Clock getClock() {
        return Clock.systemUTC();
    }
}
