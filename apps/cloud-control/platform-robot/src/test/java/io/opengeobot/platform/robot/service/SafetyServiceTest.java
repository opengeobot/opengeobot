/*
 * Function: Safety service unit tests — emergency stop, reset, state machine
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.SafetyEvent;
import io.opengeobot.platform.robot.domain.SafetyState;
import io.opengeobot.platform.robot.dto.SafetyEventDto;
import io.opengeobot.platform.robot.dto.SafetyStateDto;
import io.opengeobot.platform.robot.repository.SafetyEventRepository;
import io.opengeobot.platform.robot.repository.SafetyStateRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.opengeobot.platform.robot.web.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SafetyService}. Covers the SM-SAFETY-001 state machine
 * (NORMAL → EMERGENCY_STOPPED → RESETTING → NORMAL), event creation, outbox
 * writes, and pre-mission safety checks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SafetyServiceTest {

    @Mock private SafetyStateRepository safetyStateRepository;
    @Mock private SafetyEventRepository safetyEventRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private AuditService auditService;
    @Mock private ActorResolver actorResolver;
    @Mock private ClockProvider clockProvider;
    @Mock private PublicIdGenerator idGenerator;

    private SafetyService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        when(actorResolver.currentActor()).thenReturn("user_001");
        when(actorResolver.currentTraceId()).thenReturn("trace_001");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(idGenerator.generate(any(String.class))).thenReturn("sevt_001");
        service = new SafetyService(safetyStateRepository, safetyEventRepository,
                outboxRepository, auditService, actorResolver, clockProvider,
                idGenerator, objectMapper);
    }

    private SafetyState createStoppedState() {
        SafetyState state = new SafetyState();
        state.setRobotId("rbt_001");
        state.setState("EMERGENCY_STOPPED");
        state.setReason("Manual E-Stop");
        state.setUpdatedAt(java.time.OffsetDateTime.now(ZoneOffset.UTC));
        return state;
    }

    @Test
    void emergencyStop_createsStateIfMissingAndTransitionsToStopped() {
        when(safetyStateRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        SafetyStateDto result = service.emergencyStop("rbt_001", "Manual E-Stop");

        assertNotNull(result);
        assertEquals("EMERGENCY_STOPPED", result.state());
        assertEquals("rbt_001", result.robotId());
        assertEquals("Manual E-Stop", result.reason());

        ArgumentCaptor<SafetyState> stateCaptor = ArgumentCaptor.forClass(SafetyState.class);
        verify(safetyStateRepository).insert(stateCaptor.capture());
        assertEquals("rbt_001", stateCaptor.getValue().getRobotId());

        verify(safetyEventRepository).insert(any(SafetyEvent.class));
        verify(outboxRepository).save(any());
        verify(auditService).record(any());
    }

    @Test
    void emergencyStop_transitionsExistingNormalStateToStopped() {
        SafetyState existing = new SafetyState();
        existing.setRobotId("rbt_001");
        existing.setState("NORMAL");
        when(safetyStateRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        SafetyStateDto result = service.emergencyStop("rbt_001", "Button pressed");

        assertEquals("EMERGENCY_STOPPED", result.state());
        verify(safetyStateRepository).insertOrUpdate(existing);
        verify(safetyEventRepository).insert(any(SafetyEvent.class));
    }

    @Test
    void emergencyStop_blankRobotIdDefaultsToGlobal() {
        when(safetyStateRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        SafetyStateDto result = service.emergencyStop("  ", "Global stop");

        assertEquals("global", result.robotId());
        assertEquals("EMERGENCY_STOPPED", result.state());
    }

    @Test
    void reset_transitionsStoppedToNormal() {
        when(safetyStateRepository.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(createStoppedState());

        SafetyStateDto result = service.reset("rbt_001");

        assertEquals("NORMAL", result.state());
        assertTrue(result.reason().startsWith("Reset by"));
        verify(safetyStateRepository).updateById(any(SafetyState.class));
        verify(safetyEventRepository).insert(any(SafetyEvent.class));
        verify(outboxRepository).save(any());
        verify(auditService).record(any());
    }

    @Test
    void reset_noStateFoundThrowsConflict() {
        when(safetyStateRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.reset("rbt_999"));

        assertTrue(ex.getMessage().contains("rbt_999"));
        verify(safetyStateRepository, never()).updateById(any());
    }

    @Test
    void reset_notInStoppedStateThrowsConflict() {
        SafetyState normal = new SafetyState();
        normal.setRobotId("rbt_001");
        normal.setState("NORMAL");
        when(safetyStateRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(normal);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.reset("rbt_001"));

        assertTrue(ex.getMessage().contains("not in EMERGENCY_STOPPED"));
    }

    @Test
    void getState_noStateReturnsDefaultNormal() {
        when(safetyStateRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        SafetyStateDto result = service.getState("rbt_new");

        assertEquals("NORMAL", result.state());
        assertNull(result.lastEventAt());
        assertNull(result.reason());
        assertNull(result.updatedAt());
    }

    @Test
    void getState_existingStateReturnsCurrentValues() {
        SafetyState state = createStoppedState();
        when(safetyStateRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(state);

        SafetyStateDto result = service.getState("rbt_001");

        assertEquals("EMERGENCY_STOPPED", result.state());
        assertEquals("Manual E-Stop", result.reason());
    }

    @Test
    void safetyCheck_normalStateReturnsTrue() {
        SafetyState normal = new SafetyState();
        normal.setRobotId("rbt_001");
        normal.setState("NORMAL");
        when(safetyStateRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(normal);

        boolean safe = service.safetyCheck("rbt_001", "msn_001");

        assertTrue(safe);
        verify(safetyEventRepository).insert(any(SafetyEvent.class));
    }

    @Test
    void safetyCheck_stoppedStateReturnsFalse() {
        when(safetyStateRepository.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(createStoppedState());

        boolean safe = service.safetyCheck("rbt_001", "msn_001");

        assertFalse(safe);
        verify(safetyEventRepository).insert(any(SafetyEvent.class));
    }

    @Test
    void safetyCheck_noStateReturnsTrue() {
        when(safetyStateRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        boolean safe = service.safetyCheck("rbt_new", "msn_001");

        assertTrue(safe);
    }

    @Test
    void getEvents_returnsPagedEvents() {
        Page<SafetyEvent> page = new Page<>(1, 10);
        SafetyEvent event1 = new SafetyEvent();
        event1.setEventId("sevt_001");
        event1.setRobotId("rbt_001");
        event1.setEventType("EMERGENCY_STOP");
        event1.setReason("Manual");
        page.setRecords(List.of(event1));
        page.setTotal(1);
        when(safetyEventRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        PageResult<SafetyEventDto> result = service.getEvents("rbt_001", null, PageRequest.of(1, 10));

        assertEquals(1, result.items().size());
        assertEquals("sevt_001", result.items().get(0).eventId());
        assertEquals(1, result.total());
    }
}
