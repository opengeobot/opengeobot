/*
 * Function: Robot model REST controller — endpoints for F-ROBOT-002
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.CreateRobotModelRequest;
import io.opengeobot.platform.robot.dto.RobotModelDto;
import io.opengeobot.platform.robot.dto.UpdateRobotModelRequest;
import io.opengeobot.platform.robot.service.RobotModelService;
import io.opengeobot.platform.robot.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST controller for robot model management. Exposes endpoints under
 * {@code /api/v1/robot-models} per the F-ROBOT-002 scope.
 * Permissions: {@code robot.model.read} for GET,
 * {@code robot.model.manage} for POST/PUT/DELETE.
 */
@RestController
@RequestMapping("/api/v1/robot-models")
public class RobotModelController {

    private final RobotModelService modelService;

    public RobotModelController(RobotModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('robot.model.read')")
    public PageResponse<RobotModelDto> listModels(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<RobotModelDto> result = modelService.list(PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('robot.model.manage')")
    public ResponseEntity<RobotModelDto> createModel(@Valid @RequestBody CreateRobotModelRequest request) {
        RobotModelDto created = modelService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/robot-models/" + created.modelId()))
                .body(created);
    }

    @GetMapping("/{modelId}")
    @PreAuthorize("hasAuthority('robot.model.read')")
    public RobotModelDto getModel(@PathVariable String modelId) {
        return modelService.getByModelId(modelId);
    }

    @PutMapping("/{modelId}")
    @PreAuthorize("hasAuthority('robot.model.manage')")
    public RobotModelDto updateModel(@PathVariable String modelId,
                                     @Valid @RequestBody UpdateRobotModelRequest request) {
        return modelService.update(modelId, request);
    }

    @DeleteMapping("/{modelId}")
    @PreAuthorize("hasAuthority('robot.model.manage')")
    public ResponseEntity<Void> deleteModel(@PathVariable String modelId) {
        modelService.delete(modelId);
        return ResponseEntity.noContent().build();
    }
}
