/*
 * Function: Map REST controller — endpoints for map and area management
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.CreateMapAreaRequest;
import io.opengeobot.platform.robot.dto.CreateMapRequest;
import io.opengeobot.platform.robot.dto.CreateRestrictedAreaRequest;
import io.opengeobot.platform.robot.dto.MapAreaDto;
import io.opengeobot.platform.robot.dto.MapInfoDto;
import io.opengeobot.platform.robot.dto.RestrictedAreaDto;
import io.opengeobot.platform.robot.dto.UpdateMapRequest;
import io.opengeobot.platform.robot.service.MapService;
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
 * REST controller for map and area management. Exposes endpoints under
 * {@code /api/v1/maps} per the OpenAPI contract. Maps follow the
 * SM-MAP-001 state machine (DRAFT → PUBLISHED → ARCHIVED). Permissions:
 * {@code map_scene.map.read} for GET, {@code map_scene.map.manage} for
 * POST/PUT, {@code map_scene.map.publish} for publish.
 */
@RestController
@RequestMapping("/api/v1/maps")
public class MapController {

    private final MapService mapService;

    public MapController(MapService mapService) {
        this.mapService = mapService;
    }

    @GetMapping
    public PageResponse<MapInfoDto> listMaps(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        PageResult<MapInfoDto> result = mapService.listMaps(status, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @PostMapping
    public ResponseEntity<MapInfoDto> createMap(@Valid @RequestBody CreateMapRequest request) {
        MapInfoDto created = mapService.createMap(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{mapId}")
    public MapInfoDto getMap(@PathVariable String mapId) {
        return mapService.getMap(mapId);
    }

    @PutMapping("/{mapId}")
    public MapInfoDto updateMap(@PathVariable String mapId,
                                @Valid @RequestBody UpdateMapRequest request) {
        return mapService.updateMap(mapId, request);
    }

    @PostMapping("/{mapId}/publish")
    public MapInfoDto publishMap(@PathVariable String mapId) {
        return mapService.publishMap(mapId);
    }

    @GetMapping("/{mapId}/areas")
    public PageResponse<MapAreaDto> listAreas(
            @PathVariable String mapId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<MapAreaDto> result = mapService.listAreas(mapId, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @PostMapping("/{mapId}/areas")
    public ResponseEntity<MapAreaDto> createArea(@PathVariable String mapId,
                                                  @Valid @RequestBody CreateMapAreaRequest request) {
        MapAreaDto created = mapService.createArea(mapId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{mapId}/restricted-areas")
    public PageResponse<RestrictedAreaDto> listRestrictedAreas(
            @PathVariable String mapId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<RestrictedAreaDto> result = mapService.listRestrictedAreas(mapId, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @PostMapping("/{mapId}/restricted-areas")
    public ResponseEntity<RestrictedAreaDto> createRestrictedArea(@PathVariable String mapId,
                                                                   @Valid @RequestBody CreateRestrictedAreaRequest request) {
        RestrictedAreaDto created = mapService.createRestrictedArea(mapId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
