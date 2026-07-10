/*
 * Function: Edge gateway service unit tests — register, activate, revoke, heartbeat, rotate
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.EdgeGateway;
import io.opengeobot.platform.robot.domain.EdgeGatewayCertificate;
import io.opengeobot.platform.robot.dto.ActivateEdgeGatewayRequest;
import io.opengeobot.platform.robot.dto.CreateEdgeGatewayRequest;
import io.opengeobot.platform.robot.dto.EdgeGatewayCertificateDto;
import io.opengeobot.platform.robot.dto.EdgeGatewayDto;
import io.opengeobot.platform.robot.dto.EdgeGatewayHeartbeatRequest;
import io.opengeobot.platform.robot.dto.RevokeEdgeGatewayRequest;
import io.opengeobot.platform.robot.dto.RotateCertificateRequest;
import io.opengeobot.platform.robot.repository.EdgeGatewayCertificateRepository;
import io.opengeobot.platform.robot.repository.EdgeGatewayRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.opengeobot.platform.robot.web.ConflictException;
import io.opengeobot.platform.robot.web.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EdgeGatewayService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EdgeGatewayServiceTest {

    @Mock private EdgeGatewayRepository gatewayRepository;
    @Mock private EdgeGatewayCertificateRepository certificateRepository;
    @Mock private AuditService auditService;
    @Mock private ActorResolver actorResolver;
    @Mock private ClockProvider clockProvider;
    @Mock private PublicIdGenerator idGenerator;

    private EdgeGatewayService service;
    private final AtomicInteger idSeq = new AtomicInteger(1);

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        when(actorResolver.currentActor()).thenReturn("user_001");
        when(actorResolver.currentTraceId()).thenReturn("trace_001");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(idGenerator.generate(any(String.class))).thenAnswer(invocation -> {
            String prefix = invocation.getArgument(0);
            return prefix + "_" + String.format("%03d", idSeq.getAndIncrement());
        });
        service = new EdgeGatewayService(gatewayRepository, certificateRepository, auditService,
                actorResolver, clockProvider, idGenerator, objectMapper);
    }

    private EdgeGateway createGateway(String gatewayId, String status) {
        EdgeGateway entity = new EdgeGateway();
        entity.setId(1L);
        entity.setGatewayId(gatewayId);
        entity.setName("Edge-1");
        entity.setOrgId("org_001");
        entity.setStatus(status);
        entity.setRuntimeVersion("0.1.0");
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return entity;
    }

    @Test
    void register_insertsRegisteredGateway() {
        CreateEdgeGatewayRequest request = new CreateEdgeGatewayRequest(
                "Edge-2", "org_001", "rbt_001", "0.1.0", null, null);

        EdgeGatewayDto result = service.register(request);

        assertTrue(result.gatewayId().startsWith("gw_"));
        assertEquals("REGISTERED", result.status());
        assertEquals("Edge-2", result.name());
        assertEquals("rbt_001", result.boundRobotId());

        ArgumentCaptor<EdgeGateway> captor = ArgumentCaptor.forClass(EdgeGateway.class);
        verify(gatewayRepository).insert(captor.capture());
        assertEquals("REGISTERED", captor.getValue().getStatus());
        verify(auditService).record(any());
    }

    @Test
    void list_returnsPagedGateways() {
        EdgeGateway gateway = createGateway("gw_001", "ACTIVE");
        Page<EdgeGateway> page = new Page<>(1, 10);
        page.setRecords(List.of(gateway));
        page.setTotal(1);
        when(gatewayRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<EdgeGatewayDto> result = service.list(PageRequest.of(1, 10), "ACTIVE", null);

        assertEquals(1, result.items().size());
        assertEquals("gw_001", result.items().get(0).gatewayId());
    }

    @Test
    void getByGatewayId_notFoundThrows() {
        when(gatewayRepository.findByGatewayId("gw_missing")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.getByGatewayId("gw_missing"));
    }

    @Test
    void activate_fromRegisteredSetsActive() {
        when(gatewayRepository.findByGatewayId("gw_001"))
                .thenReturn(createGateway("gw_001", "REGISTERED"));

        EdgeGatewayDto result = service.activate("gw_001", new ActivateEdgeGatewayRequest("APPROVED"));

        assertEquals("ACTIVE", result.status());
        verify(gatewayRepository).updateById(any(EdgeGateway.class));
        verify(auditService).record(any());
    }

    @Test
    void activate_revokedThrowsConflict() {
        when(gatewayRepository.findByGatewayId("gw_001"))
                .thenReturn(createGateway("gw_001", "REVOKED"));

        assertThrows(ConflictException.class,
                () -> service.activate("gw_001", new ActivateEdgeGatewayRequest(null)));
        verify(gatewayRepository, never()).updateById(any(EdgeGateway.class));
    }

    @Test
    void revoke_marksRevokedAndRevokesActiveCert() {
        when(gatewayRepository.findByGatewayId("gw_001"))
                .thenReturn(createGateway("gw_001", "ACTIVE"));
        EdgeGatewayCertificate cert = new EdgeGatewayCertificate();
        cert.setId(1L);
        cert.setCertId("cert_001");
        cert.setGatewayId("gw_001");
        cert.setStatus("ACTIVE");
        when(certificateRepository.findActiveByGatewayId("gw_001")).thenReturn(cert);

        EdgeGatewayDto result = service.revoke("gw_001", new RevokeEdgeGatewayRequest("COMPROMISED"));

        assertEquals("REVOKED", result.status());
        ArgumentCaptor<EdgeGatewayCertificate> certCaptor =
                ArgumentCaptor.forClass(EdgeGatewayCertificate.class);
        verify(certificateRepository).updateById(certCaptor.capture());
        assertEquals("REVOKED", certCaptor.getValue().getStatus());
    }

    @Test
    void heartbeat_updatesLastHeartbeatAndRuntime() {
        when(gatewayRepository.findByGatewayId("gw_001"))
                .thenReturn(createGateway("gw_001", "ACTIVE"));

        EdgeGatewayDto result = service.heartbeat("gw_001",
                new EdgeGatewayHeartbeatRequest("0.2.0", 7L));

        assertEquals("0.2.0", result.runtimeVersion());
        assertNotNull(result.lastHeartbeatAt());
        verify(gatewayRepository).updateById(any(EdgeGateway.class));
    }

    @Test
    void heartbeat_revokedThrowsConflict() {
        when(gatewayRepository.findByGatewayId("gw_001"))
                .thenReturn(createGateway("gw_001", "REVOKED"));

        assertThrows(ConflictException.class,
                () -> service.heartbeat("gw_001", new EdgeGatewayHeartbeatRequest(null, null)));
    }

    @Test
    void rotateCertificate_marksPreviousRotatedAndInsertsActive() {
        when(gatewayRepository.findByGatewayId("gw_001"))
                .thenReturn(createGateway("gw_001", "ACTIVE"));
        EdgeGatewayCertificate previous = new EdgeGatewayCertificate();
        previous.setId(1L);
        previous.setCertId("cert_old");
        previous.setGatewayId("gw_001");
        previous.setStatus("ACTIVE");
        when(certificateRepository.findActiveByGatewayId("gw_001")).thenReturn(previous);

        OffsetDateTime expires = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1);
        EdgeGatewayCertificateDto result = service.rotateCertificate("gw_001",
                new RotateCertificateRequest("sha256:new", null, expires));

        assertTrue(result.certId().startsWith("cert_"));
        assertEquals("ACTIVE", result.status());
        assertEquals("sha256:new", result.fingerprint());

        ArgumentCaptor<EdgeGatewayCertificate> previousCaptor =
                ArgumentCaptor.forClass(EdgeGatewayCertificate.class);
        verify(certificateRepository).updateById(previousCaptor.capture());
        assertEquals("ROTATED", previousCaptor.getValue().getStatus());
        verify(certificateRepository).insert(any(EdgeGatewayCertificate.class));
        verify(gatewayRepository).updateById(any(EdgeGateway.class));
    }
}
