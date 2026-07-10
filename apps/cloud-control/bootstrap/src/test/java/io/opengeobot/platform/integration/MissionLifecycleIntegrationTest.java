/*
 * Function: Real mission lifecycle integration tests - DB-backed create, start, cancel
 * Time: 2026-07-09
 * Author: AxeXie
 */
package io.opengeobot.platform.integration;

import io.opengeobot.platform.robot.dto.CreateMissionRequest;
import io.opengeobot.platform.robot.dto.MissionDto;
import io.opengeobot.platform.robot.dto.MissionStepDto;
import io.opengeobot.platform.robot.dto.RevisePlanRequest;
import io.opengeobot.platform.robot.service.MissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real integration tests for the mission lifecycle backed by a PostgreSQL
 * container. Each test calls {@link MissionService} directly (no mocks) and
 * verifies that database records and outbox events are persisted correctly.
 *
 * <p>{@code @Transactional} ensures each test method runs in a transaction
 * that is rolled back at the end, providing full test isolation.
 */
@Transactional
class MissionLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MissionService missionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String SEEDED_ROBOT_ID = "rbt_01J00000000000000000000001";

    private MissionStepDto step(int order, String skillId) {
        return new MissionStepDto(null, null, skillId, order, Map.of(), null,
                null, null, null, null);
    }

    private MissionDto createMission() {
        CreateMissionRequest request = new CreateMissionRequest(
                "Integration Test Mission",
                "Created by integration test",
                SEEDED_ROBOT_ID,
                "NORMAL",
                null,
                List.of(step(1, "skl_nav"))
        );
        return missionService.create(request);
    }

    private void transitionToExecuting(String missionId) {
        RevisePlanRequest planRequest = new RevisePlanRequest(
                List.of(step(1, "skl_nav"))
        );
        missionService.revisePlan(missionId, planRequest);
        missionService.start(missionId);
    }

    @Test
    void createMission_persistsToDatabase() {
        MissionDto result = createMission();

        assertNotNull(result.missionId());
        assertEquals("PENDING", result.status());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT mission_id, name, status, robot_id FROM mission.mission WHERE mission_id = ?",
                result.missionId());

        assertEquals(result.missionId(), row.get("mission_id"));
        assertEquals("Integration Test Mission", row.get("name"));
        assertEquals("PENDING", row.get("status"));
        assertEquals(SEEDED_ROBOT_ID, row.get("robot_id"));

        Long eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_governance.outbox_event WHERE aggregate_id = ? AND event_type = ?",
                Long.class, result.missionId(), "mission.created.v1");
        assertNotNull(eventCount);
        assertEquals(1L, eventCount, "mission.created.v1 outbox event should be created");
    }

    @Test
    void startMission_transitionsToExecutingAndCreatesOutboxEvent() {
        MissionDto created = createMission();
        transitionToExecuting(created.missionId());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM mission.mission WHERE mission_id = ?",
                String.class, created.missionId());
        assertEquals("EXECUTING", status);

        Long startedEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_governance.outbox_event WHERE aggregate_id = ? AND event_type = ?",
                Long.class, created.missionId(), "mission.started.v1");
        assertNotNull(startedEventCount);
        assertEquals(1L, startedEventCount, "mission.started.v1 outbox event should be created");

        Object startedAt = jdbcTemplate.queryForObject(
                "SELECT started_at FROM mission.mission WHERE mission_id = ?",
                Object.class, created.missionId());
        assertNotNull(startedAt, "started_at should be populated");
    }

    @Test
    void cancelMission_transitionsToCancelledAndCreatesOutboxEvent() {
        MissionDto created = createMission();
        transitionToExecuting(created.missionId());

        MissionDto cancelled = missionService.cancel(created.missionId());
        assertEquals("CANCELLED", cancelled.status());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM mission.mission WHERE mission_id = ?",
                String.class, created.missionId());
        assertEquals("CANCELLED", status);

        Object completedAt = jdbcTemplate.queryForObject(
                "SELECT completed_at FROM mission.mission WHERE mission_id = ?",
                Object.class, created.missionId());
        assertNotNull(completedAt, "completed_at should be populated for cancelled mission");

        Long cancelEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_governance.outbox_event WHERE aggregate_id = ? AND event_type = ?",
                Long.class, created.missionId(), "mission.cancelled.v1");
        assertNotNull(cancelEventCount);
        assertEquals(1L, cancelEventCount, "mission.cancelled.v1 outbox event should be created");
    }

    @Test
    void pauseAndResume_maintainsExecutingState() {
        MissionDto created = createMission();
        transitionToExecuting(created.missionId());

        MissionDto paused = missionService.pause(created.missionId());
        assertEquals("PAUSED", paused.status());

        String pausedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM mission.mission WHERE mission_id = ?",
                String.class, created.missionId());
        assertEquals("PAUSED", pausedStatus);

        MissionDto resumed = missionService.resume(created.missionId());
        assertEquals("EXECUTING", resumed.status());

        String resumedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM mission.mission WHERE mission_id = ?",
                String.class, created.missionId());
        assertEquals("EXECUTING", resumedStatus);
    }
}
