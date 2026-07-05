/*
 * Function: Skill REST controller — endpoints for skill lifecycle management
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.CreateSkillRequest;
import io.opengeobot.platform.robot.dto.SkillDto;
import io.opengeobot.platform.robot.dto.SkillVersionDto;
import io.opengeobot.platform.robot.dto.UpdateSkillRequest;
import io.opengeobot.platform.robot.service.SkillService;
import io.opengeobot.platform.robot.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for skill lifecycle management. Exposes endpoints under
 * {@code /api/v1/skills} per the OpenAPI contract. Skills follow the
 * SM-SKILL-001 state machine (DRAFT → PUBLISHED → DEPRECATED/DISABLED).
 * Permissions: {@code skill.skill.read} for GET,
 * {@code skill.skill.manage} for POST/PUT/disable/enable,
 * {@code skill.skill.publish} for publish.
 */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public PageResponse<SkillDto> listSkills(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String module) {
        PageResult<SkillDto> result = skillService.listSkills(status, module, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @PostMapping
    public ResponseEntity<SkillDto> createSkill(@Valid @RequestBody CreateSkillRequest request) {
        SkillDto created = skillService.createSkill(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{skillId}")
    public SkillDto getSkill(@PathVariable String skillId) {
        return skillService.getSkill(skillId);
    }

    @PutMapping("/{skillId}")
    public SkillDto updateSkill(@PathVariable String skillId,
                                @Valid @RequestBody UpdateSkillRequest request) {
        return skillService.updateSkill(skillId, request);
    }

    @PostMapping("/{skillId}/publish")
    public SkillDto publishSkill(@PathVariable String skillId,
                                 @RequestBody(required = false) PublishSkillRequest request) {
        return skillService.publishSkill(skillId, request != null ? request.changelog() : null);
    }

    @PostMapping("/{skillId}/disable")
    public SkillDto disableSkill(@PathVariable String skillId) {
        return skillService.disableSkill(skillId);
    }

    @PostMapping("/{skillId}/enable")
    public SkillDto enableSkill(@PathVariable String skillId) {
        return skillService.enableSkill(skillId);
    }

    @GetMapping("/{skillId}/versions")
    public PageResponse<SkillVersionDto> listVersions(
            @PathVariable String skillId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<SkillVersionDto> result = skillService.listVersions(skillId, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    /**
     * Optional request body for the publish endpoint, carrying a changelog.
     */
    public record PublishSkillRequest(String changelog) {
    }
}
