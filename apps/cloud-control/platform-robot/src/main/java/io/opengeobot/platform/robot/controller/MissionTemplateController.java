/*
 * Function: Mission template controller — REST endpoints for F-MISSION-002
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.CreateMissionTemplateRequest;
import io.opengeobot.platform.robot.dto.MissionTemplateDto;
import io.opengeobot.platform.robot.service.MissionService;
import io.opengeobot.platform.robot.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST controller exposing the mission template API for F-MISSION-002.
 * Templates are reusable blueprints whose {@code steps} JSONB document is a
 * plan blueprint for fast mission creation.
 */
@RestController
@RequestMapping("/api/v1/mission-templates")
public class MissionTemplateController {

    private final MissionService missionService;

    public MissionTemplateController(MissionService missionService) {
        this.missionService = missionService;
    }

    @GetMapping
    public PageResponse<MissionTemplateDto> listTemplates(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        PageResult<MissionTemplateDto> result = missionService.listTemplates(page, pageSize);
        return PageResponse.of(result);
    }

    @PostMapping
    public ResponseEntity<MissionTemplateDto> createTemplate(@Valid @RequestBody CreateMissionTemplateRequest request) {
        MissionTemplateDto created = missionService.createTemplate(request);
        return ResponseEntity.created(URI.create("/api/v1/mission-templates/" + created.templateId()))
                .body(created);
    }
}
