/*
 * Function: Safety REST controller — endpoints for emergency stop, reset and state queries
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.EmergencyStopRequest;
import io.opengeobot.platform.robot.dto.ResetRequest;
import io.opengeobot.platform.robot.dto.SafetyEventDto;
import io.opengeobot.platform.robot.dto.SafetyStateDto;
import io.opengeobot.platform.robot.service.SafetyService;
import io.opengeobot.platform.robot.web.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for safety state management. Exposes endpoints under
 * {@code /api/v1/safety} per the OpenAPI contract. The safety state follows
 * the SM-SAFETY-001 state machine (NORMAL → EMERGENCY_STOPPED → RESETTING
 * → NORMAL). Permissions: {@code policy.safety.read} for GET,
 * {@code policy.safety.manage} for POST.
 */
@RestController
@RequestMapping("/api/v1/safety")
public class SafetyController {

    private final SafetyService safetyService;

    public SafetyController(SafetyService safetyService) {
        this.safetyService = safetyService;
    }

    @PostMapping("/emergency-stop")
    public ResponseEntity<SafetyStateDto> emergencyStop(@RequestBody(required = false) EmergencyStopRequest request) {
        String robotId = request != null ? request.robotId() : null;
        String reason = request != null ? request.reason() : null;
        SafetyStateDto state = safetyService.emergencyStop(robotId, reason);
        return ResponseEntity.ok(state);
    }

    @PostMapping("/reset")
    public ResponseEntity<SafetyStateDto> reset(@RequestBody(required = false) ResetRequest request) {
        String robotId = request != null ? request.robotId() : null;
        SafetyStateDto state = safetyService.reset(robotId);
        return ResponseEntity.ok(state);
    }

    @GetMapping("/state")
    public SafetyStateDto getState(@RequestParam(required = false) String robotId) {
        return safetyService.getState(robotId);
    }

    @GetMapping("/events")
    public PageResponse<SafetyEventDto> getEvents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String robotId,
            @RequestParam(required = false) String eventType) {
        PageResult<SafetyEventDto> result = safetyService.getEvents(robotId, eventType, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }
}
