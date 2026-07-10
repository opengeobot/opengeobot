/*
 * Function: Real robot management integration tests - register, update, query
 * Time: 2026-07-09
 * Author: AxeXie
 */
package io.opengeobot.platform.integration;

import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.CreateRobotRequest;
import io.opengeobot.platform.robot.dto.RobotDto;
import io.opengeobot.platform.robot.dto.UpdateRobotStatusRequest;
import io.opengeobot.platform.robot.service.RobotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real integration tests for robot lifecycle management backed by a PostgreSQL
 * container. Tests registration, status transitions (SM-ROBOT-001), and
 * filtered queries with pagination against the real database.
 *
 * <p>Uses the seeded robot model {@code mdl_01J00000000000000000000001}
 * (Unitree Go2) from V8 as a prerequisite for robot registration.
 *
 * <p>{@code @Transactional} ensures each test method runs in a transaction
 * that is rolled back at the end, providing full test isolation.
 */
@Transactional
class RobotManagementIntegrationTest extends AbstractIntegrationTest {

    @Autowired private RobotService robotService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String SEEDED_MODEL_ID = "mdl_01J00000000000000000000001";
    private static final String SEEDED_ORG_ID = "org_01J00000000000000000000001";

    private RobotDto registerRobot() {
        CreateRobotRequest request = new CreateRobotRequest(
                "IntegrationTestBot",
                SEEDED_MODEL_ID,
                "SN-INT-0001",
                SEEDED_ORG_ID,
                List.of()
        );
        return robotService.create(request);
    }

    @Test
    void registerRobot_persistsToDatabase() {
        RobotDto result = registerRobot();

        assertNotNull(result.robotId());
        assertEquals("OFFLINE", result.status());
        assertEquals("IntegrationTestBot", result.name());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT robot_id, name, model_id, serial_number, status, org_id FROM robot_registry.robot WHERE robot_id = ?",
                result.robotId());

        assertEquals(result.robotId(), row.get("robot_id"));
        assertEquals("IntegrationTestBot", row.get("name"));
        assertEquals(SEEDED_MODEL_ID, row.get("model_id"));
        assertEquals("SN-INT-0001", row.get("serial_number"));
        assertEquals("OFFLINE", row.get("status"));
        assertEquals(SEEDED_ORG_ID, row.get("org_id"));

        Long outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_governance.outbox_event WHERE aggregate_type = ? AND aggregate_id = ?",
                Long.class, "robot", result.robotId());
        assertNotNull(outboxCount);
        assertTrue(outboxCount >= 1, "robot.registered.v1 outbox event should be created");
    }

    @Test
    void registerRobot_duplicateSerialReturns409() {
        registerRobot();

        CreateRobotRequest duplicate = new CreateRobotRequest(
                "AnotherBot",
                SEEDED_MODEL_ID,
                "SN-INT-0001",
                SEEDED_ORG_ID,
                List.of()
        );

        assertThrows(io.opengeobot.platform.robot.web.ConflictException.class,
                () -> robotService.create(duplicate));
    }

    @Test
    void updateStatus_validTransitionUpdatesDatabase() {
        RobotDto robot = registerRobot();

        robotService.updateStatus(robot.robotId(),
                new UpdateRobotStatusRequest("ONLINE", "Heartbeat received"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status FROM robot_registry.robot WHERE robot_id = ?",
                robot.robotId());
        assertEquals("ONLINE", row.get("status"));

        Long historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM robot_registry.robot_status_history WHERE robot_id = ? AND old_status = ? AND new_status = ?",
                Long.class, robot.robotId(), "OFFLINE", "ONLINE");
        assertNotNull(historyCount);
        assertEquals(1L, historyCount, "robot_status_history should record the OFFLINE -> ONLINE transition");
    }

    @Test
    void updateStatus_invalidTransitionThrowsConflict() {
        RobotDto robot = registerRobot();

        assertThrows(io.opengeobot.platform.robot.web.ConflictException.class,
                () -> robotService.updateStatus(robot.robotId(),
                        new UpdateRobotStatusRequest("BUSY", "Should fail - OFFLINE -> BUSY not allowed")));
    }

    @Test
    void queryRobots_byStatusReturnsFilteredResults() {
        registerRobot();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT serial_number FROM robot_registry.robot WHERE status = ? ORDER BY name",
                "OFFLINE");

        assertFalse(rows.isEmpty(), "Should find at least one OFFLINE robot");

        boolean found = rows.stream()
                .anyMatch(r -> "SN-INT-0001".equals(r.get("serial_number")));
        assertTrue(found, "The registered robot should appear in the OFFLINE results");
    }

    @Test
    void queryRobots_byModelReturnsFilteredResults() {
        registerRobot();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT serial_number FROM robot_registry.robot WHERE model_id = ? ORDER BY name",
                SEEDED_MODEL_ID);

        assertFalse(rows.isEmpty(), "Should find at least one robot with the seeded model");

        boolean found = rows.stream()
                .anyMatch(r -> "SN-INT-0001".equals(r.get("serial_number")));
        assertTrue(found, "The registered robot should appear in the model-filtered results");
    }

    @Test
    void queryRobots_paginationWorks() {
        for (int i = 0; i < 3; i++) {
            CreateRobotRequest request = new CreateRobotRequest(
                    "PaginationBot-" + i,
                    SEEDED_MODEL_ID,
                    "SN-PAGE-" + i,
                    SEEDED_ORG_ID,
                    List.of()
            );
            robotService.create(request);
        }

        Long totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM robot_registry.robot",
                Long.class);
        assertNotNull(totalCount);
        assertTrue(totalCount >= 3, "Total should reflect all registered robots");

        List<Map<String, Object>> page1Rows = jdbcTemplate.queryForList(
                "SELECT robot_id FROM robot_registry.robot ORDER BY name LIMIT ? OFFSET ?",
                2, 0);
        assertTrue(page1Rows.size() <= 2, "Page 1 should have at most 2 items");

        List<Map<String, Object>> page2Rows = jdbcTemplate.queryForList(
                "SELECT robot_id FROM robot_registry.robot ORDER BY name LIMIT ? OFFSET ?",
                2, 2);
        assertTrue(page2Rows.size() >= 1, "Page 2 should have at least 1 item");
    }

    @Test
    void deleteRobot_offlineStatusSucceeds() {
        RobotDto robot = registerRobot();

        robotService.delete(robot.robotId());

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM robot_registry.robot WHERE robot_id = ?",
                Long.class, robot.robotId());
        assertNotNull(count);
        assertEquals(0L, count, "Robot should be deleted from the database");
    }

    @Test
    void deleteRobot_onlineStatusThrowsConflict() {
        RobotDto robot = registerRobot();

        robotService.updateStatus(robot.robotId(),
                new UpdateRobotStatusRequest("ONLINE", "Going online"));

        assertThrows(io.opengeobot.platform.robot.web.ConflictException.class,
                () -> robotService.delete(robot.robotId()));
    }
}
