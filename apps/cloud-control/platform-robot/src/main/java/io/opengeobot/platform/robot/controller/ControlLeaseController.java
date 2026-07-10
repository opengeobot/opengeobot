/*
 * Function: Control lease REST controller — acquire/release for F-MONITOR-001
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.robot.dto.AcquireControlLeaseRequest;
import io.opengeobot.platform.robot.dto.ControlLeaseDto;
import io.opengeobot.platform.robot.service.ControlLeaseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST controller for robot control leases under
 * {@code /api/v1/robots/{robotId}/control-leases}.
 */
@RestController
@RequestMapping("/api/v1/robots/{robotId}/control-leases")
public class ControlLeaseController {

    private final ControlLeaseService controlLeaseService;

    public ControlLeaseController(ControlLeaseService controlLeaseService) {
        this.controlLeaseService = controlLeaseService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('robot.robot.control')")
    public ResponseEntity<ControlLeaseDto> getActive(@PathVariable String robotId) {
        ControlLeaseDto lease = controlLeaseService.getActive(robotId);
        if (lease == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(lease);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('robot.robot.control')")
    public ResponseEntity<ControlLeaseDto> acquire(
            @PathVariable String robotId,
            @Valid @RequestBody(required = false) AcquireControlLeaseRequest request) {
        ControlLeaseDto created = controlLeaseService.acquire(robotId, request);
        return ResponseEntity.created(
                        URI.create("/api/v1/robots/" + robotId + "/control-leases"))
                .body(created);
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('robot.robot.control')")
    public ControlLeaseDto release(@PathVariable String robotId) {
        return controlLeaseService.release(robotId);
    }
}
