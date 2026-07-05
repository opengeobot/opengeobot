/*
 * Function: TriggerFailoverRequest DTO — trigger a manual fleet failover
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for triggering a manual failover. The mission is transferred
 * from the source robot to a target robot chosen by the scheduler or specified
 * by the caller. Jackson serialises field names in snake_case globally.
 *
 * @param robotId        public identifier of the source robot
 * @param missionId      public identifier of the mission to transfer
 * @param reason         machine-readable reason for the failover
 * @param targetRobotId  optional explicit target robot
 */
public record TriggerFailoverRequest(
        @NotBlank(message = "robot_id must not be blank")
        String robotId,

        @NotBlank(message = "mission_id must not be blank")
        String missionId,

        @NotBlank(message = "reason must not be blank")
        String reason,

        String targetRobotId
) {
}
