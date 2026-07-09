/*
 * Function: Media service unit tests — upload, download, delete with MinIO mock
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.config.MinioConfig;
import io.opengeobot.platform.robot.domain.MediaObject;
import io.opengeobot.platform.robot.dto.MediaObjectDto;
import io.opengeobot.platform.robot.repository.MediaObjectRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.opengeobot.platform.robot.web.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MediaService}. Covers upload to MinIO, download,
 * delete (both MinIO object and metadata), and listing with filters.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MediaServiceTest {

    @Mock private MediaObjectRepository mediaObjectRepository;
    @Mock private MinioClient minioClient;
    @Mock private MinioConfig minioConfig;
    @Mock private AuditService auditService;
    @Mock private ActorResolver actorResolver;
    @Mock private ClockProvider clockProvider;
    @Mock private PublicIdGenerator idGenerator;

    private MediaService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        when(actorResolver.currentActor()).thenReturn("user_001");
        when(actorResolver.currentTraceId()).thenReturn("trace_001");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(idGenerator.generate(any(String.class))).thenReturn("med_001");
        when(minioConfig.getBucket()).thenReturn("test-bucket");
        service = new MediaService(mediaObjectRepository, minioClient, minioConfig,
                auditService, actorResolver, clockProvider, idGenerator, objectMapper);
    }

    private MultipartFile createMockFile(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private MediaObject createMediaObject(String mediaId) {
        MediaObject entity = new MediaObject();
        entity.setId(1L);
        entity.setMediaId(mediaId);
        entity.setFileName("test.jpg");
        entity.setFilePath("media/med_001.jpg");
        entity.setFileSize(1024L);
        entity.setContentType("image/jpeg");
        entity.setMediaType("IMAGE");
        entity.setRobotId("rbt_001");
        entity.setMissionId("msn_001");
        entity.setUploadedBy("user_001");
        entity.setUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return entity;
    }

    @Test
    void upload_storesFileInMinioAndRecordsMetadata() throws Exception {
        MultipartFile file = createMockFile("test.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});
        when(minioClient.bucketExists(any())).thenReturn(true);

        MediaObjectDto result = service.upload(file, "IMAGE", "rbt_001", "msn_001", null);

        assertEquals("med_001", result.mediaId());
        assertEquals("test.jpg", result.fileName());
        assertEquals("IMAGE", result.mediaType());
        assertEquals("image/jpeg", result.contentType());
        assertEquals(4, result.fileSize());

        verify(minioClient).putObject(any(PutObjectArgs.class));
        ArgumentCaptor<MediaObject> captor = ArgumentCaptor.forClass(MediaObject.class);
        verify(mediaObjectRepository).insert((MediaObject) captor.capture());
        assertEquals("med_001", captor.getValue().getMediaId());
        verify(auditService).record(any());
    }

    @Test
    void upload_nullMediaTypeDefaultsToDocument() throws Exception {
        MultipartFile file = createMockFile("doc.pdf", "application/pdf", new byte[]{1});
        when(minioClient.bucketExists(any())).thenReturn(true);

        MediaObjectDto result = service.upload(file, null, "rbt_001", null, null);

        assertEquals("DOCUMENT", result.mediaType());
    }

    @Test
    void upload_minioFailureThrowsIllegalState() throws Exception {
        MultipartFile file = createMockFile("test.jpg", "image/jpeg", new byte[]{1});
        when(minioClient.bucketExists(any())).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new RuntimeException("MinIO down"));

        assertThrows(IllegalStateException.class,
                () -> service.upload(file, "IMAGE", "rbt_001", null, null));
        verify(mediaObjectRepository, never()).insert(any(MediaObject.class));
    }

    @Test
    void download_returnsInputStreamFromMinio() throws Exception {
        when(mediaObjectRepository.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(createMediaObject("med_001"));
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenReturn(mock(GetObjectResponse.class));

        InputStream result = service.download("med_001");

        assertNotNull(result);
    }

    @Test
    void download_mediaNotFoundThrows() {
        when(mediaObjectRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.download("med_999"));
    }

    @Test
    void getMedia_returnsDto() {
        when(mediaObjectRepository.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(createMediaObject("med_001"));

        MediaObjectDto result = service.getMedia("med_001");

        assertEquals("med_001", result.mediaId());
        assertEquals("test.jpg", result.fileName());
    }

    @Test
    void getMedia_notFoundThrows() {
        when(mediaObjectRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.getMedia("med_999"));
    }

    @Test
    void delete_removesMinioObjectAndMetadata() throws Exception {
        MediaObject entity = createMediaObject("med_001");
        when(mediaObjectRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        service.delete("med_001");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        verify(mediaObjectRepository).deleteById(1L);
        verify(auditService).record(any());
    }

    @Test
    void delete_minioFailureStillDeletesMetadata() throws Exception {
        MediaObject entity = createMediaObject("med_001");
        when(mediaObjectRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);
        doThrow(new RuntimeException("MinIO error"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        service.delete("med_001");

        verify(mediaObjectRepository).deleteById(1L);
        verify(auditService).record(any());
    }

    @Test
    void delete_mediaNotFoundThrows() {
        when(mediaObjectRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.delete("med_999"));
    }

    @Test
    void listMedia_returnsPagedResults() {
        MediaObject entity = createMediaObject("med_001");
        Page<MediaObject> page = new Page<>(1, 10);
        page.setRecords(List.of(entity));
        page.setTotal(1);
        when(mediaObjectRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<MediaObjectDto> result = service.listMedia("rbt_001", null, null, PageRequest.of(1, 10));

        assertEquals(1, result.items().size());
        assertEquals("med_001", result.items().get(0).mediaId());
    }
}
