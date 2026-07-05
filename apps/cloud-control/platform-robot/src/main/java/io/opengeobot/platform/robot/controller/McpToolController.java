/*
 * Function: MCP tool REST controller — endpoints for F-MCP-001 MCP tool gateway
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.InvokeToolRequest;
import io.opengeobot.platform.robot.dto.InvocationResultDto;
import io.opengeobot.platform.robot.dto.McpToolDto;
import io.opengeobot.platform.robot.dto.RegisterToolRequest;
import io.opengeobot.platform.robot.service.McpToolService;
import io.opengeobot.platform.robot.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the MCP tool gateway. Exposes endpoints under
 * {@code /api/v1/mcp/tools} per the OpenAPI contract. Tools follow the
 * lifecycle (DRAFT, ACTIVE, DEPRECATED, DISABLED). Permissions:
 * {@code mcp.tool.read} for GET, {@code mcp.tool.manage} for POST,
 * {@code mcp.tool.invoke} for invocation.
 */
@RestController
@RequestMapping("/api/v1/mcp/tools")
public class McpToolController {

    private final McpToolService mcpToolService;

    public McpToolController(McpToolService mcpToolService) {
        this.mcpToolService = mcpToolService;
    }

    @GetMapping
    public PageResponse<McpToolDto> listTools(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        PageResult<McpToolDto> result = mcpToolService.listTools(status, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @PostMapping
    public ResponseEntity<McpToolDto> registerTool(@Valid @RequestBody RegisterToolRequest request) {
        McpToolDto created = mcpToolService.registerTool(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{toolId}")
    public McpToolDto getTool(@PathVariable String toolId) {
        return mcpToolService.getTool(toolId);
    }

    @PostMapping("/{toolId}/invoke")
    public InvocationResultDto invokeTool(@PathVariable String toolId,
                                          @Valid @RequestBody InvokeToolRequest request) {
        return mcpToolService.invoke(toolId, request);
    }

    @GetMapping("/{toolId}/invocations")
    public PageResponse<InvocationResultDto> listInvocations(
            @PathVariable String toolId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<InvocationResultDto> result = mcpToolService.listInvocations(toolId, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }
}
