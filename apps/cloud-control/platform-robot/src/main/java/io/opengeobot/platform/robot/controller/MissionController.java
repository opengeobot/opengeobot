/*
 * Function: Mission controller — REST endpoints for F-MISSION-001/002/003
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.ApprovalRequest;
import io.opengeobot.platform.robot.dto.CreateMissionRequest;
import io.opengeobot.platform.robot.dto.MissionApprovalDto;
import io.opengeobot.platform.robot.dto.MissionDto;
import io.opengeobot.platform.robot.dto.RevisePlanRequest;
import io.opengeobot.platform.robot.dto.UpdateMissionRequest;
import io.opengeobot.platform.robot.service.MissionService;
import io.opengeobot.platform.robot.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * REST controller exposing the mission management API for
 * F-MISSION-001/002/003. All endpoints are prefixed with {@code /api/v1}.
 * State transitions are delegated to {@link MissionService} which enforces the
 * SM-MISSION-001 state machine and writes audit + outbox events.
 */
@RestController
@RequestMapping("/api/v1/missions")
public class MissionController {

    private final MissionService missionService;

    public MissionController(MissionService missionService) {
        this.missionService = missionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('mission.mission.read')")
    public PageResponse<MissionDto> listMissions(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "robot_id", required = false) String robotId) {
        PageResult<MissionDto> result = missionService.list(status, robotId, page, pageSize);
        return PageResponse.of(result);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('mission.mission.create')")
    public ResponseEntity<MissionDto> createMission(@Valid @RequestBody CreateMissionRequest request) {
        MissionDto created = missionService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/missions/" + created.missionId()))
                .body(created);
    }

    @GetMapping("/{missionId}")
    @PreAuthorize("hasAuthority('mission.mission.read')")
    public MissionDto getMission(@PathVariable String missionId) {
        return missionService.getDetail(missionId);
    }

    @PutMapping("/{missionId}")
    @PreAuthorize("hasAuthority('mission.mission.create')")
    public MissionDto updateMission(@PathVariable String missionId, @RequestBody UpdateMissionRequest request) {
        return missionService.update(missionId, request);
    }

    @PostMapping("/{missionId}/plan")
    @PreAuthorize("hasAuthority('mission.mission.create')")
    public MissionDto revisePlan(@PathVariable String missionId, @Valid @RequestBody RevisePlanRequest request) {
        return missionService.revisePlan(missionId, request);
    }

    @PostMapping("/{missionId}/start")
    @PreAuthorize("hasAuthority('mission.mission.create')")
    public MissionDto startMission(@PathVariable String missionId) {
        return missionService.start(missionId);
    }

    @PostMapping("/{missionId}/pause")
    @PreAuthorize("hasAuthority('mission.mission.pause')")
    public MissionDto pauseMission(@PathVariable String missionId) {
        return missionService.pause(missionId);
    }

    @PostMapping("/{missionId}/resume")
    @PreAuthorize("hasAuthority('mission.mission.pause')")
    public MissionDto resumeMission(@PathVariable String missionId) {
        return missionService.resume(missionId);
    }

    @PostMapping("/{missionId}/cancel")
    @PreAuthorize("hasAuthority('mission.mission.cancel')")
    public MissionDto cancelMission(@PathVariable String missionId) {
        return missionService.cancel(missionId);
    }

    @PostMapping("/{missionId}/submit-approval")
    @PreAuthorize("hasAuthority('mission.mission.create')")
    public MissionApprovalDto submitApproval(@PathVariable String missionId) {
        return missionService.submitApproval(missionId);
    }

    @PostMapping("/{missionId}/approve")
    @PreAuthorize("hasAuthority('mission.mission.approve')")
    public MissionApprovalDto approveMission(@PathVariable String missionId, @RequestBody ApprovalRequest request) {
        return missionService.approve(missionId, request);
    }

    @PostMapping("/{missionId}/reject")
    @PreAuthorize("hasAuthority('mission.mission.approve')")
    public MissionApprovalDto rejectMission(@PathVariable String missionId, @RequestBody ApprovalRequest request) {
        return missionService.reject(missionId, request);
    }
}
