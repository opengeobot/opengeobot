/*
 * Function: Adapter REST controller - compatibility and health endpoints for F-ADAPTER-002
 * Time: 2026-07-09
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.robot.dto.AdapterCompatibilityDto;
import io.opengeobot.platform.robot.dto.UpdateAdapterHealthRequest;
import io.opengeobot.platform.robot.service.AdapterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for adapter compatibility and health management. Exposes
 * endpoints under {@code /api/v1/adapters} per the F-ADAPTER-002 scope.
 * Permissions: {@code robot.model.read} for compatibility/health queries,
 * {@code edge.adapter.manage} for health status updates.
 */
@RestController
@RequestMapping("/api/v1/adapters")
public class AdapterController {

    private final AdapterService adapterService;

    public AdapterController(AdapterService adapterService) {
        this.adapterService = adapterService;
    }

    @GetMapping("/compatibility/{robotModelId}")
    @PreAuthorize("hasAuthority('robot.model.read')")
    public List<AdapterCompatibilityDto> listCompatibility(@PathVariable String robotModelId) {
        return adapterService.listByRobotModel(robotModelId);
    }

    @GetMapping("/{adapterId}/health")
    @PreAuthorize("hasAuthority('robot.model.read')")
    public AdapterCompatibilityDto getHealth(@PathVariable String adapterId) {
        return adapterService.getHealth(adapterId);
    }

    @PutMapping("/{adapterId}/health")
    @PreAuthorize("hasAuthority('edge.adapter.manage')")
    public ResponseEntity<AdapterCompatibilityDto> updateHealth(
            @PathVariable String adapterId,
            @Valid @RequestBody UpdateAdapterHealthRequest request) {
        AdapterCompatibilityDto updated = adapterService.updateHealth(adapterId, request);
        return ResponseEntity.ok(updated);
    }
}
