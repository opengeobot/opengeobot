/*
 * Function: Alarm REST controller — endpoints for F-ALARM-001 alarm lifecycle and notification
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.AlarmEventDto;
import io.opengeobot.platform.robot.dto.AlarmRuleDto;
import io.opengeobot.platform.robot.dto.CreateAlarmRuleRequest;
import io.opengeobot.platform.robot.dto.CreateNotificationChannelRequest;
import io.opengeobot.platform.robot.dto.NotificationChannelDto;
import io.opengeobot.platform.robot.dto.UpdateAlarmRuleRequest;
import io.opengeobot.platform.robot.service.AlarmService;
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
import java.util.List;

/**
 * REST controller exposing the alarm management API for F-ALARM-001. All
 * endpoints are prefixed with {@code /api/v1/alarms}. State transitions
 * (acknowledge, resolve) follow the SM-ALARM-001 state machine
 * (ACTIVE → ACKNOWLEDGED → RESOLVED) and are delegated to
 * {@link AlarmService} which writes audit + outbox events.
 */
@RestController
@RequestMapping("/api/v1/alarms")
public class AlarmController {

    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ops.alarm.read')")
    public PageResponse<AlarmEventDto> listAlarms(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "severity", required = false) String severity,
            @RequestParam(name = "source", required = false) String source) {
        PageResult<AlarmEventDto> result = alarmService.list(status, severity, source,
                PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('ops.alarm.manage')")
    public ResponseEntity<AlarmRuleDto> createRule(@Valid @RequestBody CreateAlarmRuleRequest request) {
        AlarmRuleDto created = alarmService.createRule(request);
        return ResponseEntity.created(URI.create("/api/v1/alarms/rules/" + created.ruleId()))
                .body(created);
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('ops.alarm.read')")
    public PageResponse<AlarmRuleDto> listRules(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "enabled", required = false) Boolean enabled) {
        PageResult<AlarmRuleDto> result = alarmService.listRules(source, enabled,
                PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @PutMapping("/rules/{ruleId}")
    @PreAuthorize("hasAuthority('ops.alarm.manage')")
    public AlarmRuleDto updateRule(@PathVariable String ruleId,
                                   @RequestBody UpdateAlarmRuleRequest request) {
        return alarmService.updateRule(ruleId, request);
    }

    @PostMapping("/{alarmId}/acknowledge")
    @PreAuthorize("hasAuthority('ops.alarm.manage')")
    public AlarmEventDto acknowledge(@PathVariable String alarmId) {
        return alarmService.acknowledge(alarmId);
    }

    @PostMapping("/{alarmId}/resolve")
    @PreAuthorize("hasAuthority('ops.alarm.manage')")
    public AlarmEventDto resolve(@PathVariable String alarmId) {
        return alarmService.resolve(alarmId);
    }

    @GetMapping("/channels")
    @PreAuthorize("hasAuthority('ops.alarm.read')")
    public List<NotificationChannelDto> listChannels() {
        return alarmService.listChannels();
    }

    @PostMapping("/channels")
    @PreAuthorize("hasAuthority('ops.alarm.manage')")
    public ResponseEntity<NotificationChannelDto> createChannel(
            @Valid @RequestBody CreateNotificationChannelRequest request) {
        NotificationChannelDto created = alarmService.createChannel(request);
        return ResponseEntity.created(URI.create("/api/v1/alarms/channels/" + created.channelId()))
                .body(created);
    }
}
