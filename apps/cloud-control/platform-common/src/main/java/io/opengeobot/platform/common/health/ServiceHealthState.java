/*
 * Function: Service health state enum — SM-SERVICE-HEALTH state machine
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.health;

/**
 * Canonical health states for the SM-SERVICE-HEALTH state machine.
 * This is a code contract governed by the platform, not editable dictionary data.
 */
public enum ServiceHealthState {

    UNKNOWN,
    STARTING,
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    STOPPED
}
