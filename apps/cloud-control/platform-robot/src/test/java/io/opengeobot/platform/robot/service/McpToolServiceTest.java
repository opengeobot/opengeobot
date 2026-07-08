/*
 * Function: McpToolService unit tests — real tool execution via NATS and HTTP
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.event.NatsConnectionManager;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.McpInvocationLog;
import io.opengeobot.platform.robot.domain.McpTool;
import io.opengeobot.platform.robot.dto.InvokeToolRequest;
import io.opengeobot.platform.robot.dto.InvocationResultDto;
import io.opengeobot.platform.robot.repository.McpInvocationLogRepository;
import io.opengeobot.platform.robot.repository.McpToolRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link McpToolService} tool execution. Verifies that
 * invocations route to configured handlers (NATS, HTTP) and that tools
 * without a handler fail properly rather than faking success.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpToolServiceTest {

    @Mock private McpToolRepository toolRepository;
    @Mock private McpInvocationLogRepository invocationLogRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private AuditService auditService;
    @Mock private ActorResolver actorResolver;
    @Mock private ClockProvider clockProvider;
    @Mock private PublicIdGenerator idGenerator;
    @Mock private ObjectProvider<NatsConnectionManager> natsProvider;
    @Mock private NatsConnectionManager natsManager;
    @Mock private Connection natsConnection;
    @Mock private Message natsReply;

    private McpToolService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(actorResolver.currentActor()).thenReturn("test-user");
        when(actorResolver.currentTraceId()).thenReturn("trace-001");
        when(idGenerator.generate(anyString())).thenReturn("id-001");
        when(invocationLogRepository.insert(any(McpInvocationLog.class))).thenReturn(1);
        doNothing().when(outboxRepository).save(any());

        service = new McpToolService(
                toolRepository, invocationLogRepository, outboxRepository,
                auditService, actorResolver, clockProvider, idGenerator,
                objectMapper, natsProvider, 5000);
    }

    private McpTool createTool(String toolId, String handlerType, String handlerEndpoint) {
        McpTool tool = new McpTool();
        tool.setToolId(toolId);
        tool.setName("test_tool");
        tool.setStatus("ACTIVE");
        tool.setCanaryPercent(0);
        tool.setHandlerType(handlerType);
        tool.setHandlerEndpoint(handlerEndpoint);
        return tool;
    }

    @Test
    void invoke_noHandlerConfigured_returnsFailed() {
        McpTool tool = createTool("mcp_001", null, null);
        when(toolRepository.selectOne(any())).thenReturn(tool);

        InvocationResultDto result = service.invoke("mcp_001",
                new InvokeToolRequest(Map.of("param", "value")));

        assertEquals("FAILED", result.status());
        assertNotNull(result.error());
        assertTrue(result.error().contains("no handler configured"));
    }

    @Test
    void invoke_natsHandler_successfulResponse_returnsSuccess() throws Exception {
        McpTool tool = createTool("mcp_002", "NATS", "opengeobot.tools.test");
        when(toolRepository.selectOne(any())).thenReturn(tool);
        when(natsProvider.getIfAvailable()).thenReturn(natsManager);
        when(natsManager.isConnected()).thenReturn(true);
        when(natsManager.getConnection()).thenReturn(natsConnection);
        String responseBody = "{\"result\":\"ok\",\"value\":42}";
        when(natsReply.getData()).thenReturn(responseBody.getBytes(StandardCharsets.UTF_8));
        when(natsConnection.request(anyString(), any(byte[].class), any(java.time.Duration.class)))
                .thenReturn(natsReply);

        InvocationResultDto result = service.invoke("mcp_002",
                new InvokeToolRequest(Map.of("param", "value")));

        assertEquals("SUCCESS", result.status());
        assertNotNull(result.output());
        assertEquals("ok", result.output().get("result"));
        assertEquals(42, result.output().get("value"));
    }

    @Test
    void invoke_natsHandler_natsNotAvailable_returnsFailed() {
        McpTool tool = createTool("mcp_003", "NATS", "opengeobot.tools.test");
        when(toolRepository.selectOne(any())).thenReturn(tool);
        when(natsProvider.getIfAvailable()).thenReturn(natsManager);
        when(natsManager.isConnected()).thenReturn(false);
        when(natsManager.tryConnect()).thenReturn(false);

        InvocationResultDto result = service.invoke("mcp_003",
                new InvokeToolRequest(Map.of("param", "value")));

        assertEquals("FAILED", result.status());
        assertNotNull(result.error());
        assertTrue(result.error().contains("NATS is not available"));
    }

    @Test
    void invoke_natsHandler_timeout_returnsTimeout() throws Exception {
        McpTool tool = createTool("mcp_004", "NATS", "opengeobot.tools.test");
        when(toolRepository.selectOne(any())).thenReturn(tool);
        when(natsProvider.getIfAvailable()).thenReturn(natsManager);
        when(natsManager.isConnected()).thenReturn(true);
        when(natsManager.getConnection()).thenReturn(natsConnection);
        when(natsConnection.request(anyString(), any(byte[].class), any(java.time.Duration.class)))
                .thenReturn(null);

        InvocationResultDto result = service.invoke("mcp_004",
                new InvokeToolRequest(Map.of("param", "value")));

        assertEquals("TIMEOUT", result.status());
        assertNotNull(result.error());
        assertTrue(result.error().contains("timed out"));
    }

    @Test
    void invoke_unsupportedHandlerType_returnsFailed() {
        McpTool tool = createTool("mcp_005", "FTP", "ftp://example.com/tool");
        when(toolRepository.selectOne(any())).thenReturn(tool);

        InvocationResultDto result = service.invoke("mcp_005",
                new InvokeToolRequest(Map.of("param", "value")));

        assertEquals("FAILED", result.status());
        assertTrue(result.error().contains("Unsupported handler type"));
    }

    @Test
    void invoke_natsHandler_success_logsInvocationAndAudit() throws Exception {
        McpTool tool = createTool("mcp_006", "NATS", "opengeobot.tools.test");
        when(toolRepository.selectOne(any())).thenReturn(tool);
        when(natsProvider.getIfAvailable()).thenReturn(natsManager);
        when(natsManager.isConnected()).thenReturn(true);
        when(natsManager.getConnection()).thenReturn(natsConnection);
        String responseBody = "{\"status\":\"ok\"}";
        when(natsReply.getData()).thenReturn(responseBody.getBytes(StandardCharsets.UTF_8));
        when(natsConnection.request(anyString(), any(byte[].class), any(java.time.Duration.class)))
                .thenReturn(natsReply);

        service.invoke("mcp_006", new InvokeToolRequest(Map.of("param", "value")));

        ArgumentCaptor<McpInvocationLog> logCaptor = ArgumentCaptor.forClass(McpInvocationLog.class);
        verify(invocationLogRepository).insert(logCaptor.capture());
        assertEquals("SUCCESS", logCaptor.getValue().getStatus());
        verify(auditService).record(any());
        verify(outboxRepository).save(any());
    }

    @Test
    void invoke_toolNotActive_throwsConflict() {
        McpTool tool = createTool("mcp_007", "NATS", "opengeobot.tools.test");
        tool.setStatus("DRAFT");
        when(toolRepository.selectOne(any())).thenReturn(tool);

        assertThrows(io.opengeobot.platform.robot.web.ConflictException.class, () ->
                service.invoke("mcp_007", new InvokeToolRequest(Map.of("param", "value"))));
    }
}
