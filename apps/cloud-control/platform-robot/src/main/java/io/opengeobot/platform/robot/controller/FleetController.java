/*
 * Function: FleetController — REST endpoints for multi-robot fleet scheduling
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.robot.dto.ConflictRecordDto;
import io.opengeobot.platform.robot.dto.FailoverEventDto;
import io.opengeobot.platform.robot.dto.FleetScheduleDto;
import io.opengeobot.platform.robot.service.FleetService;
import io.opengeobot.platform.robot.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/fleet")
public class FleetController {

    private final FleetService fleetService;

    public FleetController(FleetService fleetService) {
        this.fleetService = fleetService;
    }

    @GetMapping("/schedule")
    public PageResponse<FleetScheduleDto> listSchedules(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size,
            @RequestParam(required = false) String status) {
        return PageResponse.of(fleetService.listSchedules(PageRequest.of(page, page_size), status));
    }

    @PostMapping("/schedule")
    public ResponseEntity<FleetScheduleDto> createSchedule(@RequestBody Map<String, Object> body) {
        var dto = fleetService.createSchedule(
                (String) body.get("mission_id"),
                (String) body.get("robot_id"),
                body.get("planned_start") != null ? OffsetDateTime.parse((String) body.get("planned_start")) : null,
                body.get("planned_end") != null ? OffsetDateTime.parse((String) body.get("planned_end")) : null,
                (String) body.get("priority"));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/conflicts")
    public PageResponse<ConflictRecordDto> listConflicts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size) {
        return PageResponse.of(fleetService.listConflicts(PageRequest.of(page, page_size)));
    }

    @PostMapping("/conflicts/{conflictId}/resolve")
    public ResponseEntity<Void> resolveConflict(@PathVariable String conflictId, @RequestBody Map<String, String> body) {
        fleetService.resolveConflict(conflictId, body.get("resolution"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/failovers")
    public PageResponse<FailoverEventDto> listFailovers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size) {
        return PageResponse.of(fleetService.listFailovers(PageRequest.of(page, page_size)));
    }

    @PostMapping("/failovers")
    public ResponseEntity<FailoverEventDto> triggerFailover(@RequestBody Map<String, String> body) {
        var dto = fleetService.triggerFailover(
                body.get("robot_id"),
                body.get("mission_id"),
                body.get("reason"),
                body.get("target_robot_id"));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
