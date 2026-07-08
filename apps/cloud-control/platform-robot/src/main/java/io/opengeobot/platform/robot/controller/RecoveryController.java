/*
 * Function: Recovery REST controller — endpoints for backup, restore and drills
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.domain.BackupRecord;
import io.opengeobot.platform.robot.dto.BackupRecordDto;
import io.opengeobot.platform.robot.dto.CreateDrillRequest;
import io.opengeobot.platform.robot.dto.DrillRecordDto;
import io.opengeobot.platform.robot.dto.RestoreRecordDto;
import io.opengeobot.platform.robot.dto.RestoreRequest;
import io.opengeobot.platform.robot.service.BackupService;
import io.opengeobot.platform.robot.service.DrillService;
import io.opengeobot.platform.robot.service.RestoreService;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.opengeobot.platform.robot.web.PageResponse;
import io.opengeobot.platform.robot.web.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for backup and recovery (F-RECOVERY-001). Exposes endpoints
 * under {@code /api/v1/recovery} per the OpenAPI contract. Backups follow
 * SM-BACKUP-OPERATION (RUNNING → COMPLETED / FAILED). Permissions:
 * {@code ops.backup.read} for GET, {@code ops.backup.manage} for POST backup,
 * {@code ops.restore.execute} for restore.
 */
@RestController
@RequestMapping("/api/v1/recovery")
public class RecoveryController {

    private final BackupService backupService;
    private final RestoreService restoreService;
    private final DrillService drillService;
    private final ActorResolver actorResolver;

    public RecoveryController(BackupService backupService,
                              RestoreService restoreService,
                              DrillService drillService,
                              ActorResolver actorResolver) {
        this.backupService = backupService;
        this.restoreService = restoreService;
        this.drillService = drillService;
        this.actorResolver = actorResolver;
    }

    @GetMapping("/backups")
    @PreAuthorize("hasAuthority('ops.backup.read')")
    public PageResponse<BackupRecordDto> listBackups(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        PageResult<BackupRecordDto> result =
                backupService.listBackups(type, status, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @PostMapping("/backups")
    @PreAuthorize("hasAuthority('ops.backup.manage')")
    public ResponseEntity<BackupRecordDto> triggerBackup(@Valid @RequestBody BackupTypeRequest request) {
        String actor = actorResolver.currentActor();
        BackupRecordDto created;
        if ("MINIO".equalsIgnoreCase(request.type())) {
            created = backupService.backupMinIO(actor);
        } else {
            created = backupService.backupDatabase(actor);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/restore")
    @PreAuthorize("hasAuthority('ops.restore.execute')")
    public ResponseEntity<RestoreRecordDto> restore(@Valid @RequestBody RestoreRequest request) {
        BackupRecord backup = backupService.findBackupByBackupId(request.backupId());
        if (backup == null) {
            throw new ResourceNotFoundException("Backup '" + request.backupId() + "' not found");
        }
        String actor = actorResolver.currentActor();
        RestoreRecordDto created;
        if ("MINIO".equalsIgnoreCase(backup.getType())) {
            created = restoreService.restoreMinIO(request.backupId(), actor);
        } else {
            created = restoreService.restoreDatabase(request.backupId(), actor);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/drills")
    @PreAuthorize("hasAuthority('ops.backup.read')")
    public PageResponse<DrillRecordDto> listDrills(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<DrillRecordDto> result =
                drillService.listDrills(PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @PostMapping("/drills")
    @PreAuthorize("hasAuthority('ops.backup.manage')")
    public ResponseEntity<DrillRecordDto> createDrill(@Valid @RequestBody CreateDrillRequest request) {
        DrillRecordDto created = drillService.createDrill(request.type(), request.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Request body for triggering a manual backup.
     */
    public record BackupTypeRequest(String type) {
    }
}
