/*
 * Function: Monitor REST controller — real-time monitoring endpoints for F-MONITOR-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.robot.dto.MissionMonitorInfo;
import io.opengeobot.platform.robot.dto.MonitorOverview;
import io.opengeobot.platform.robot.dto.RobotMonitorInfo;
import io.opengeobot.platform.robot.dto.TakeoverRequest;
import io.opengeobot.platform.robot.service.MonitorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the real-time monitoring API for F-MONITOR-001.
 * All endpoints are prefixed with {@code /api/v1/monitor}. Read endpoints are
 * delegated to {@link MonitorService}; the takeover action pauses the active
 * mission and transitions the robot to MAINTENANCE.
 */
@RestController
@RequestMapping("/api/v1/monitor")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/robots/{robotId}")
    @PreAuthorize("hasAuthority('monitor.robot.view')")
    public RobotMonitorInfo getRobotMonitorInfo(@PathVariable String robotId) {
        return monitorService.getRobotMonitorInfo(robotId);
    }

    @GetMapping("/missions/{missionId}")
    @PreAuthorize("hasAuthority('monitor.fleet.view')")
    public MissionMonitorInfo getMissionMonitorInfo(@PathVariable String missionId) {
        return monitorService.getMissionMonitorInfo(missionId);
    }

    @PostMapping("/robots/{robotId}/takeover")
    @PreAuthorize("hasAuthority('robot.robot.control')")
    public RobotMonitorInfo takeover(@PathVariable String robotId,
                                     @RequestBody(required = false) TakeoverRequest request) {
        String reason = (request != null) ? request.reason() : null;
        return monitorService.takeover(robotId, reason);
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('monitor.fleet.view')")
    public MonitorOverview getOverview() {
        return monitorService.getOverview();
    }
}
