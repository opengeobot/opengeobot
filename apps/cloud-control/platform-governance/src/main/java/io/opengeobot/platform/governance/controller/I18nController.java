/*
 * Function: I18n REST controller — endpoints for internationalization resources
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.governance.dto.BatchI18nRequest;
import io.opengeobot.platform.governance.dto.BatchI18nResultDto;
import io.opengeobot.platform.governance.dto.CreateI18nResourceRequest;
import io.opengeobot.platform.governance.dto.I18nResourceDto;
import io.opengeobot.platform.governance.dto.UpdateI18nResourceRequest;
import io.opengeobot.platform.governance.service.I18nService;
import io.opengeobot.platform.governance.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * REST controller for internationalization resource management. Exposes
 * endpoints under {@code /api/v1/i18n} per the OpenAPI contract.
 * Permissions: {@code platform.i18n.read} for GET,
 * {@code platform.i18n.manage} for POST/PUT/DELETE.
 */
@RestController
@RequestMapping("/api/v1/i18n")
public class I18nController {

    private final I18nService i18nService;

    public I18nController(I18nService i18nService) {
        this.i18nService = i18nService;
    }

    @GetMapping
    public PageResponse<I18nResourceDto> listResources(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String resourceKey) {
        PageResult<I18nResourceDto> result = i18nService.listResources(locale, module, resourceKey, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @PostMapping
    public ResponseEntity<I18nResourceDto> createResource(@Valid @RequestBody CreateI18nResourceRequest request) {
        I18nResourceDto created = i18nService.createResource(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{resourceKey}")
    public I18nResourceDto updateResource(@PathVariable String resourceKey,
                                           @RequestParam String locale,
                                           @Valid @RequestBody UpdateI18nResourceRequest request) {
        return i18nService.updateResource(resourceKey, locale, request);
    }

    @DeleteMapping("/{resourceKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteResource(@PathVariable String resourceKey, @RequestParam String locale) {
        i18nService.deleteResource(resourceKey, locale);
    }

    @PostMapping("/batch")
    public BatchI18nResultDto batchImport(@Valid @RequestBody BatchI18nRequest request) {
        return i18nService.batchImport(request);
    }
}
