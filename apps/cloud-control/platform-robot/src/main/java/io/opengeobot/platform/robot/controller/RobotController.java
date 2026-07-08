/*
 * Function: Robot REST controller — endpoints for F-ROBOT-001 robot management
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.CreateRobotRequest;
import io.opengeobot.platform.robot.dto.RobotCapabilityDto;
import io.opengeobot.platform.robot.dto.RobotDto;
import io.opengeobot.platform.robot.dto.UpdateRobotRequest;
import io.opengeobot.platform.robot.dto.UpdateRobotStatusRequest;
import io.opengeobot.platform.robot.service.RobotService;
import io.opengeobot.platform.robot.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * REST controller for robot lifecycle management. Exposes endpoints under
 * {@code /api/v1/robots} per the OpenAPI contract. The {@code status} field
 * follows the SM-ROBOT-001 state machine
 * (ONLINE, OFFLINE, BUSY, ERROR, MAINTENANCE).
 * Permissions: {@code robot.robot.read} for GET endpoints,
 * {@code robot.robot.register} for POST/PUT/DELETE/PATCH endpoints.
 */
@RestController
@RequestMapping("/api/v1/robots")
public class RobotController {

    private final RobotService robotService;

    public RobotController(RobotService robotService) {
        this.robotService = robotService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('robot.robot.read')")
    public PageResponse<RobotDto> listRobots(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String modelId,
            @RequestParam(required = false) String orgId) {
        PageResult<RobotDto> result = robotService.list(
                PageRequest.of(page, pageSize), status, modelId, orgId);
        return PageResponse.of(result);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('robot.robot.register')")
    public ResponseEntity<RobotDto> createRobot(@Valid @RequestBody CreateRobotRequest request) {
        RobotDto created = robotService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/robots/" + created.robotId()))
                .body(created);
    }

    @GetMapping("/{robotId}")
    @PreAuthorize("hasAuthority('robot.robot.read')")
    public RobotDto getRobot(@PathVariable String robotId) {
        return robotService.getByRobotId(robotId);
    }

    @PutMapping("/{robotId}")
    @PreAuthorize("hasAuthority('robot.robot.register')")
    public RobotDto updateRobot(@PathVariable String robotId,
                                @Valid @RequestBody UpdateRobotRequest request) {
        return robotService.update(robotId, request);
    }

    @DeleteMapping("/{robotId}")
    @PreAuthorize("hasAuthority('robot.robot.register')")
    public ResponseEntity<Void> deleteRobot(@PathVariable String robotId) {
        robotService.delete(robotId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{robotId}/status")
    @PreAuthorize("hasAuthority('robot.robot.register')")
    public ResponseEntity<Void> updateStatus(@PathVariable String robotId,
                                              @Valid @RequestBody UpdateRobotStatusRequest request) {
        robotService.updateStatus(robotId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{robotId}/capabilities")
    @PreAuthorize("hasAuthority('robot.robot.read')")
    public RobotCapabilityListResponse getCapabilities(@PathVariable String robotId) {
        List<RobotCapabilityDto> capabilities = robotService.getCapabilities(robotId);
        return new RobotCapabilityListResponse(robotId, capabilities);
    }

    @PutMapping("/{robotId}/capabilities")
    @PreAuthorize("hasAuthority('robot.robot.register')")
    public RobotCapabilityListResponse updateCapabilities(@PathVariable String robotId,
                                                          @Valid @RequestBody UpdateRobotCapabilitiesRequest request) {
        robotService.updateCapabilities(robotId, request.capabilities());
        List<RobotCapabilityDto> capabilities = robotService.getCapabilities(robotId);
        return new RobotCapabilityListResponse(robotId, capabilities);
    }

    /**
     * Response wrapper for the capabilities declared by a single robot,
     * matching the {@code RobotCapabilityList} schema in the OpenAPI contract.
     */
    public record RobotCapabilityListResponse(String robotId, List<RobotCapabilityDto> capabilities) {
    }

    /**
     * Request body for replacing the full set of capabilities declared by a
     * robot, matching the {@code UpdateRobotCapabilitiesRequest} schema.
     */
    public record UpdateRobotCapabilitiesRequest(@Valid List<RobotCapabilityDto> capabilities) {
    }
}
