/*
 * Function: Config REST controller — endpoints for platform config management
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.governance.dto.ConfigDto;
import io.opengeobot.platform.governance.dto.ConfigHistoryDto;
import io.opengeobot.platform.governance.dto.CreateConfigRequest;
import io.opengeobot.platform.governance.dto.UpdateConfigRequest;
import io.opengeobot.platform.governance.service.ConfigService;
import io.opengeobot.platform.governance.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for platform configuration management. Exposes endpoints
 * under {@code /api/v1/configs} per the OpenAPI contract.
 * Permissions: {@code platform.config.read} for GET,
 * {@code platform.config.manage} for POST/PUT.
 */
@RestController
@RequestMapping("/api/v1/configs")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('platform.config.read')")
    public PageResponse<ConfigDto> listConfigs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String key) {
        PageResult<ConfigDto> result = configService.listConfigs(module, key, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('platform.config.manage')")
    public ResponseEntity<ConfigDto> createConfig(@Valid @RequestBody CreateConfigRequest request) {
        ConfigDto created = configService.createConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{configKey}")
    @PreAuthorize("hasAuthority('platform.config.read')")
    public ConfigDto getConfig(@PathVariable String configKey) {
        return configService.getConfig(configKey);
    }

    @PutMapping("/{configKey}")
    @PreAuthorize("hasAuthority('platform.config.manage')")
    public ConfigDto updateConfig(@PathVariable String configKey,
                                  @Valid @RequestBody UpdateConfigRequest request) {
        return configService.updateConfig(configKey, request);
    }

    @GetMapping("/{configKey}/history")
    @PreAuthorize("hasAuthority('platform.config.read')")
    public PageResponse<ConfigHistoryDto> getConfigHistory(
            @PathVariable String configKey,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<ConfigHistoryDto> result = configService.getConfigHistory(configKey, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }
}
