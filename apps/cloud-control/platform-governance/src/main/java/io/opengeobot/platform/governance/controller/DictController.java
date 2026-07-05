/*
 * Function: Dictionary REST controller — endpoints for dict types and items
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.governance.dto.CreateDictItemRequest;
import io.opengeobot.platform.governance.dto.CreateDictTypeRequest;
import io.opengeobot.platform.governance.dto.DictItemDto;
import io.opengeobot.platform.governance.dto.DictTypeDto;
import io.opengeobot.platform.governance.dto.UpdateDictItemRequest;
import io.opengeobot.platform.governance.dto.UpdateDictTypeRequest;
import io.opengeobot.platform.governance.service.DictService;
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
 * REST controller for dictionary type and item management. Exposes endpoints
 * under {@code /api/v1/dict} per the OpenAPI contract.
 * Permissions: {@code platform.dictionary.read} for GET,
 * {@code platform.dictionary.manage} for POST/PUT/DELETE.
 */
@RestController
@RequestMapping("/api/v1/dict")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping("/types")
    public PageResponse<DictTypeDto> listTypes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String typeCode) {
        PageResult<DictTypeDto> result = dictService.listTypes(PageRequest.of(page, pageSize), status, typeCode);
        return PageResponse.of(result);
    }

    @PostMapping("/types")
    public ResponseEntity<DictTypeDto> createType(@Valid @RequestBody CreateDictTypeRequest request) {
        DictTypeDto created = dictService.createType(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/types/{typeCode}")
    public DictTypeDto getType(@PathVariable String typeCode) {
        return dictService.getType(typeCode);
    }

    @PutMapping("/types/{typeCode}")
    public DictTypeDto updateType(@PathVariable String typeCode,
                                  @Valid @RequestBody UpdateDictTypeRequest request) {
        return dictService.updateType(typeCode, request);
    }

    @DeleteMapping("/types/{typeCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteType(@PathVariable String typeCode) {
        dictService.deleteType(typeCode);
    }

    @PostMapping("/types/{typeCode}/publish")
    public DictTypeDto publishType(@PathVariable String typeCode) {
        return dictService.publishType(typeCode);
    }

    @GetMapping("/types/{typeCode}/items")
    public PageResponse<DictItemDto> listItems(
            @PathVariable String typeCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        PageResult<DictItemDto> result = dictService.listItems(typeCode, PageRequest.of(page, pageSize), status);
        return PageResponse.of(result);
    }

    @PostMapping("/types/{typeCode}/items")
    public ResponseEntity<DictItemDto> createItem(@PathVariable String typeCode,
                                                   @Valid @RequestBody CreateDictItemRequest request) {
        DictItemDto created = dictService.createItem(typeCode, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{typeCode}/items/{itemCode}")
    public DictItemDto updateItem(@PathVariable String typeCode,
                                   @PathVariable String itemCode,
                                   @Valid @RequestBody UpdateDictItemRequest request) {
        return dictService.updateItem(typeCode, itemCode, request);
    }

    @DeleteMapping("/{typeCode}/items/{itemCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable String typeCode, @PathVariable String itemCode) {
        dictService.deleteItem(typeCode, itemCode);
    }
}
