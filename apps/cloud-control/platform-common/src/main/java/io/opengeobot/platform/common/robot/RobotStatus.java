/*
 * Function: Robot operational status enum — SM-ROBOT-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.common.robot;

/**
 * Canonical robot operational states for the SM-ROBOT-001 state machine.
 * This is a code contract governed by the platform, not editable dictionary
 * data. Physical motion is never driven directly by this status; it is an
 * observed/proposed state that must pass the platform safety pipeline.
 */
public enum RobotStatus {

    ONLINE,
    OFFLINE,
    BUSY,
    ERROR,
    MAINTENANCE
}
