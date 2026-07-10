/*
 * Function: Real safety flow integration tests - emergency stop, reset, events
 * Time: 2026-07-09
 * Author: AxeXie
 */
package io.opengeobot.platform.integration;

import io.opengeobot.platform.robot.dto.SafetyStateDto;
import io.opengeobot.platform.robot.service.SafetyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real integration tests for the safety flow backed by a PostgreSQL container.
 * Tests the SM-SAFETY-001 state machine (NORMAL -> EMERGENCY_STOPPED -> NORMAL)
 * and verifies that {@code safety_state} and {@code safety_event} records are
 * persisted correctly, including outbox events for emergency stop and reset.
 *
 * <p>{@code @Transactional} ensures each test method runs in a transaction
 * that is rolled back at the end, providing full test isolation.
 */
@Transactional
class SafetyFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private SafetyService safetyService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String TEST_ROBOT_ID = "rbt_01J00000000000000000000001";

    @Test
    void emergencyStop_createsStoppedStateAndEvent() {
        SafetyStateDto result = safetyService.emergencyStop(TEST_ROBOT_ID, "Manual E-Stop");

        assertEquals(TEST_ROBOT_ID, result.robotId());
        assertEquals("EMERGENCY_STOPPED", result.state());
        assertEquals("Manual E-Stop", result.reason());

        Map<String, Object> stateRow = jdbcTemplate.queryForMap(
                "SELECT robot_id, state, reason FROM policy.safety_state WHERE robot_id = ?",
                TEST_ROBOT_ID);

        assertEquals(TEST_ROBOT_ID, stateRow.get("robot_id"));
        assertEquals("EMERGENCY_STOPPED", stateRow.get("state"));
        assertEquals("Manual E-Stop", stateRow.get("reason"));

        Long eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM policy.safety_event WHERE robot_id = ? AND event_type = ?",
                Long.class, TEST_ROBOT_ID, "EMERGENCY_STOP");
        assertNotNull(eventCount);
        assertEquals(1L, eventCount, "EMERGENCY_STOP safety event should be recorded");

        Long outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_governance.outbox_event WHERE aggregate_type = ? AND event_type = ?",
                Long.class, "safety_event", "safety.emergency_stop.v1");
        assertNotNull(outboxCount);
        assertTrue(outboxCount >= 1, "safety.emergency_stop.v1 outbox event should be created");
    }

    @Test
    void reset_returnsStateToNormal() {
        safetyService.emergencyStop(TEST_ROBOT_ID, "Test stop");

        SafetyStateDto result = safetyService.reset(TEST_ROBOT_ID);

        assertEquals(TEST_ROBOT_ID, result.robotId());
        assertEquals("NORMAL", result.state());

        String state = jdbcTemplate.queryForObject(
                "SELECT state FROM policy.safety_state WHERE robot_id = ?",
                String.class, TEST_ROBOT_ID);
        assertEquals("NORMAL", state);

        Long resetEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM policy.safety_event WHERE robot_id = ? AND event_type = ?",
                Long.class, TEST_ROBOT_ID, "RESET");
        assertNotNull(resetEventCount);
        assertEquals(1L, resetEventCount, "RESET safety event should be recorded");

        Long outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_governance.outbox_event WHERE aggregate_type = ? AND event_type = ?",
                Long.class, "safety_event", "safety.reset.v1");
        assertNotNull(outboxCount);
        assertTrue(outboxCount >= 1, "safety.reset.v1 outbox event should be created");
    }

    @Test
    void emergencyStop_globalScope_usesGlobalRobotId() {
        SafetyStateDto result = safetyService.emergencyStop(null, "Global stop");

        assertEquals("global", result.robotId());
        assertEquals("EMERGENCY_STOPPED", result.state());

        String state = jdbcTemplate.queryForObject(
                "SELECT state FROM policy.safety_state WHERE robot_id = ?",
                String.class, "global");
        assertEquals("EMERGENCY_STOPPED", state);
    }

    @Test
    void getState_returnsDefaultForUnknownRobot() {
        SafetyStateDto result = safetyService.getState("rbt_unknown_999");

        assertEquals("rbt_unknown_999", result.robotId());
        assertEquals("NORMAL", result.state());
        assertNull(result.lastEventAt());
    }

    @Test
    void safetyCheck_passesForNormalRobot() {
        boolean safe = safetyService.safetyCheck(TEST_ROBOT_ID, "msn_test_001");
        assertTrue(safe, "Safety check should pass when robot is in NORMAL state");

        Long checkEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM policy.safety_event WHERE robot_id = ? AND event_type = ?",
                Long.class, TEST_ROBOT_ID, "SAFETY_CHECK_PASSED");
        assertNotNull(checkEventCount);
        assertEquals(1L, checkEventCount, "SAFETY_CHECK_PASSED event should be recorded");
    }

    @Test
    void safetyCheck_failsAfterEmergencyStop() {
        safetyService.emergencyStop(TEST_ROBOT_ID, "Test stop");

        boolean safe = safetyService.safetyCheck(TEST_ROBOT_ID, "msn_test_002");
        assertFalse(safe, "Safety check should fail when robot is EMERGENCY_STOPPED");

        Long checkEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM policy.safety_event WHERE robot_id = ? AND event_type = ?",
                Long.class, TEST_ROBOT_ID, "SAFETY_CHECK_FAILED");
        assertNotNull(checkEventCount);
        assertEquals(1L, checkEventCount, "SAFETY_CHECK_FAILED event should be recorded");
    }
}
