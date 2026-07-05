/*
 * Function: Memory REST controller — endpoints for task cases and suggestions
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.FeedbackRequest;
import io.opengeobot.platform.robot.dto.ImprovementSuggestionDto;
import io.opengeobot.platform.robot.dto.TaskCaseDto;
import io.opengeobot.platform.robot.service.MemoryService;
import io.opengeobot.platform.robot.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for task memory (F-MEMORY-001). Exposes endpoints under
 * {@code /api/v1/memory} per the OpenAPI contract. Permissions:
 * {@code memory.memory.read} for case GET, {@code memory.failure_case.read}
 * for suggestions GET, {@code memory.improvement.manage} for feedback POST.
 */
@RestController
@RequestMapping("/api/v1/memory")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping("/cases")
    public PageResponse<TaskCaseDto> listCases(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String robotId,
            @RequestParam(required = false) String skillId) {
        PageResult<TaskCaseDto> resultPage =
                memoryService.listCases(result, robotId, skillId, PageRequest.of(page, pageSize));
        return PageResponse.of(resultPage);
    }

    @GetMapping("/cases/{caseId}")
    public MemoryService.CaseDetail getCase(@PathVariable String caseId) {
        return memoryService.getCase(caseId);
    }

    @GetMapping("/suggestions")
    public PageResponse<ImprovementSuggestionDto> listSuggestions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        PageResult<ImprovementSuggestionDto> result =
                memoryService.listSuggestions(status, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @PostMapping("/feedback")
    public ResponseEntity<ImprovementSuggestionDto> submitFeedback(@Valid @RequestBody FeedbackRequest request) {
        ImprovementSuggestionDto updated = memoryService.submitFeedback(request);
        return ResponseEntity.ok(updated);
    }
}
