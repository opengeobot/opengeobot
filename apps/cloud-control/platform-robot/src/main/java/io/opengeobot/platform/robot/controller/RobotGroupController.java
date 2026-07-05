/*
 * Function: Robot group REST controller — endpoints for F-ROBOT-002
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.CreateRobotGroupRequest;
import io.opengeobot.platform.robot.dto.RobotGroupDto;
import io.opengeobot.platform.robot.dto.UpdateRobotGroupRequest;
import io.opengeobot.platform.robot.service.RobotGroupService;
import io.opengeobot.platform.robot.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
import java.util.List;
import java.util.Map;

/**
 * REST controller for robot group management. Exposes endpoints under
 * {@code /api/v1/robot-groups} per the F-ROBOT-002 scope.
 * Permissions: {@code robot.group.read} for GET,
 * {@code robot.group.manage} for POST/PUT/DELETE.
 */
@RestController
@RequestMapping("/api/v1/robot-groups")
public class RobotGroupController {

    private final RobotGroupService groupService;

    public RobotGroupController(RobotGroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public PageResponse<RobotGroupDto> listGroups(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String parentId) {
        PageResult<RobotGroupDto> result = groupService.list(PageRequest.of(page, pageSize), parentId);
        return PageResponse.of(result);
    }

    @PostMapping
    public ResponseEntity<RobotGroupDto> createGroup(@Valid @RequestBody CreateRobotGroupRequest request) {
        RobotGroupDto created = groupService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/robot-groups/" + created.groupId()))
                .body(created);
    }

    @GetMapping("/{groupId}")
    public RobotGroupDto getGroup(@PathVariable String groupId) {
        return groupService.getByGroupId(groupId);
    }

    @PutMapping("/{groupId}")
    public RobotGroupDto updateGroup(@PathVariable String groupId,
                                    @Valid @RequestBody UpdateRobotGroupRequest request) {
        return groupService.update(groupId, request);
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String groupId) {
        groupService.delete(groupId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/members/{robotId}")
    public ResponseEntity<Void> addMember(@PathVariable String groupId,
                                           @PathVariable String robotId) {
        groupService.addRobot(groupId, robotId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{groupId}/members/{robotId}")
    public ResponseEntity<Void> removeMember(@PathVariable String groupId,
                                              @PathVariable String robotId) {
        groupService.removeRobot(groupId, robotId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{groupId}/members")
    public Map<String, Object> listMembers(@PathVariable String groupId) {
        List<String> robotIds = groupService.getMembers(groupId);
        return Map.of("group_id", groupId, "robot_ids", robotIds);
    }
}
