/*
 * Function: Media REST controller — endpoints for media upload, download and management
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.MediaObjectDto;
import io.opengeobot.platform.robot.service.MediaService;
import io.opengeobot.platform.robot.web.PageResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.OffsetDateTime;

/**
 * REST controller for media object management. Exposes endpoints under
 * {@code /api/v1/media} per the OpenAPI contract. Media files are stored in
 * MinIO/S3; the database holds metadata. Permissions:
 * {@code media.media.read} for GET, {@code media.media.upload} for POST,
 * {@code media.media.manage} for DELETE.
 */
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('media.asset.upload')")
    public ResponseEntity<MediaObjectDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "media_type", required = false) String mediaType,
            @RequestParam(value = "robot_id", required = false) String robotId,
            @RequestParam(value = "mission_id", required = false) String missionId,
            @RequestParam(value = "expires_at", required = false) OffsetDateTime expiresAt) {
        MediaObjectDto created = mediaService.upload(file, mediaType, robotId, missionId, expiresAt);
        return ResponseEntity.ok(created);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('media.asset.read')")
    public PageResponse<MediaObjectDto> listMedia(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String robotId,
            @RequestParam(required = false) String missionId,
            @RequestParam(required = false) String mediaType) {
        PageResult<MediaObjectDto> result = mediaService.listMedia(robotId, missionId, mediaType, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @GetMapping("/{mediaId}")
    @PreAuthorize("hasAuthority('media.asset.read')")
    public MediaObjectDto getMedia(@PathVariable String mediaId) {
        return mediaService.getMedia(mediaId);
    }

    @GetMapping("/{mediaId}/download")
    @PreAuthorize("hasAuthority('media.asset.download')")
    public ResponseEntity<InputStreamResource> download(@PathVariable String mediaId) {
        MediaObjectDto meta = mediaService.getMedia(mediaId);
        InputStream stream = mediaService.download(mediaId);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + meta.fileName() + "\"");
        headers.setContentType(MediaType.parseMediaType(
                meta.contentType() != null ? meta.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE));
        if (meta.fileSize() != null) {
            headers.setContentLength(meta.fileSize());
        }
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(stream));
    }

    @DeleteMapping("/{mediaId}")
    @PreAuthorize("hasAuthority('media.asset.delete')")
    public ResponseEntity<Void> delete(@PathVariable String mediaId) {
        mediaService.delete(mediaId);
        return ResponseEntity.noContent().build();
    }
}
