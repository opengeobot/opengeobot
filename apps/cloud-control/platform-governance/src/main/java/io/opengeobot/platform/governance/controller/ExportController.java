/*
 * Function: Export REST controller — endpoints for asynchronous data export
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.controller;

import io.opengeobot.platform.governance.dto.CreateExportRequest;
import io.opengeobot.platform.governance.dto.ExportTaskDto;
import io.opengeobot.platform.governance.service.ExportService;
import io.opengeobot.platform.governance.web.ActorResolver;
import io.opengeobot.platform.governance.web.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST controller for asynchronous data export. Exposes endpoints under
 * {@code /api/v1/exports} per the OpenAPI contract.
 * Permissions depend on the exported resource type.
 */
@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

    private final ExportService exportService;
    private final ActorResolver actorResolver;

    public ExportController(ExportService exportService, ActorResolver actorResolver) {
        this.exportService = exportService;
        this.actorResolver = actorResolver;
    }

    @PostMapping
    public ResponseEntity<ExportTaskDto> createExport(@Valid @RequestBody CreateExportRequest request) {
        ExportTaskDto task = exportService.createExport(request, actorResolver.currentActor());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/v1/exports/" + task.exportId()))
                .body(task);
    }

    @GetMapping("/{exportId}")
    public ExportTaskDto getExport(@PathVariable String exportId) {
        return exportService.getExport(exportId);
    }

    @GetMapping("/{exportId}/download")
    public ResponseEntity<Void> downloadExport(@PathVariable String exportId) {
        try {
            String downloadUrl = exportService.downloadExport(exportId);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(downloadUrl))
                    .build();
        } catch (IllegalStateException e) {
            throw new ResourceNotFoundException(e.getMessage());
        }
    }
}
