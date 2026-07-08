/*
 * Function: Fleet service unit tests — scheduling, conflict resolution, failover
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.domain.ConflictRecord;
import io.opengeobot.platform.robot.domain.FailoverEvent;
import io.opengeobot.platform.robot.domain.FleetSchedule;
import io.opengeobot.platform.robot.dto.ConflictRecordDto;
import io.opengeobot.platform.robot.dto.FailoverEventDto;
import io.opengeobot.platform.robot.dto.FleetScheduleDto;
import io.opengeobot.platform.robot.repository.ConflictRecordRepository;
import io.opengeobot.platform.robot.repository.FailoverEventRepository;
import io.opengeobot.platform.robot.repository.FleetScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FleetService}. Covers schedule creation, conflict
 * resolution, and failover trigger.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FleetServiceTest {

    @Mock private FleetScheduleRepository scheduleRepository;
    @Mock private ConflictRecordRepository conflictRepository;
    @Mock private FailoverEventRepository failoverRepository;

    private FleetService service;

    @BeforeEach
    void setUp() {
        service = new FleetService(scheduleRepository, conflictRepository, failoverRepository);
    }

    @Test
    void createSchedule_insertsPendingSchedule() {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime end = start.plusHours(1);

        FleetScheduleDto result = service.createSchedule("msn_001", "rbt_001", start, end, "HIGH");

        assertNotNull(result.scheduleId());
        assertTrue(result.scheduleId().startsWith("sch_"));
        assertEquals("msn_001", result.missionId());
        assertEquals("rbt_001", result.robotId());
        assertEquals("HIGH", result.priority());
        assertEquals("PENDING", result.status());

        ArgumentCaptor<FleetSchedule> captor = ArgumentCaptor.forClass(FleetSchedule.class);
        verify(scheduleRepository).insert(captor.capture());
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals("HIGH", captor.getValue().getPriority());
    }

    @Test
    void createSchedule_nullPriorityDefaultsToNormal() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        FleetScheduleDto result = service.createSchedule("msn_001", "rbt_001", now, now.plusHours(1), null);

        assertEquals("NORMAL", result.priority());
    }

    @Test
    void listSchedules_returnsPagedSchedules() {
        FleetSchedule schedule = new FleetSchedule();
        schedule.setScheduleId("sch_001");
        schedule.setMissionId("msn_001");
        schedule.setRobotId("rbt_001");
        schedule.setPriority("NORMAL");
        schedule.setStatus("PENDING");
        Page<FleetSchedule> page = new Page<>(1, 10);
        page.setRecords(List.of(schedule));
        page.setTotal(1);
        when(scheduleRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<FleetScheduleDto> result = service.listSchedules(PageRequest.of(1, 10), null);

        assertEquals(1, result.items().size());
        assertEquals("sch_001", result.items().get(0).scheduleId());
    }

    @Test
    void listSchedules_filterByStatus() {
        Page<FleetSchedule> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(scheduleRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<FleetScheduleDto> result = service.listSchedules(PageRequest.of(1, 10), "PENDING");

        assertTrue(result.items().isEmpty());
    }

    @Test
    void resolveConflict_existingConflictUpdatesResolution() {
        ConflictRecord conflict = new ConflictRecord();
        conflict.setConflictId("cfl_001");
        conflict.setConflictType("schedule_overlap");
        when(conflictRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(conflict);

        service.resolveConflict("cfl_001", "RESCHEDULED");

        ArgumentCaptor<ConflictRecord> captor = ArgumentCaptor.forClass(ConflictRecord.class);
        verify(conflictRepository).updateById(captor.capture());
        assertEquals("RESCHEDULED", captor.getValue().getResolution());
        assertNotNull(captor.getValue().getResolvedAt());
    }

    @Test
    void resolveConflict_notFoundThrowsIllegalArgument() {
        when(conflictRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.resolveConflict("cfl_999", "IGNORED"));
        assertTrue(ex.getMessage().contains("cfl_999"));
        verify(conflictRepository, never()).updateById(any());
    }

    @Test
    void triggerFailover_insertsInitiatedEvent() {
        FailoverEventDto result = service.triggerFailover("rbt_001", "msn_001", "Hardware fault", "rbt_002");

        assertNotNull(result.failoverId());
        assertTrue(result.failoverId().startsWith("fov_"));
        assertEquals("rbt_001", result.robotId());
        assertEquals("rbt_002", result.targetRobotId());
        assertEquals("msn_001", result.missionId());
        assertEquals("Hardware fault", result.reason());
        assertEquals("INITIATED", result.status());

        ArgumentCaptor<FailoverEvent> captor = ArgumentCaptor.forClass(FailoverEvent.class);
        verify(failoverRepository).insert(captor.capture());
        assertEquals("INITIATED", captor.getValue().getStatus());
    }

    @Test
    void listConflicts_returnsPagedConflicts() {
        ConflictRecord conflict = new ConflictRecord();
        conflict.setConflictId("cfl_001");
        conflict.setConflictType("schedule_overlap");
        conflict.setDescription("Two missions overlap");
        Page<ConflictRecord> page = new Page<>(1, 10);
        page.setRecords(List.of(conflict));
        page.setTotal(1);
        when(conflictRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<ConflictRecordDto> result = service.listConflicts(PageRequest.of(1, 10));

        assertEquals(1, result.items().size());
        assertEquals("cfl_001", result.items().get(0).conflictId());
    }

    @Test
    void listFailovers_returnsPagedFailovers() {
        FailoverEvent event = new FailoverEvent();
        event.setFailoverId("fov_001");
        event.setRobotId("rbt_001");
        event.setMissionId("msn_001");
        event.setReason("Hardware fault");
        event.setTargetRobotId("rbt_002");
        event.setStatus("INITIATED");
        Page<FailoverEvent> page = new Page<>(1, 10);
        page.setRecords(List.of(event));
        page.setTotal(1);
        when(failoverRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<FailoverEventDto> result = service.listFailovers(PageRequest.of(1, 10));

        assertEquals(1, result.items().size());
        assertEquals("fov_001", result.items().get(0).failoverId());
        assertEquals("rbt_002", result.items().get(0).targetRobotId());
    }
}
