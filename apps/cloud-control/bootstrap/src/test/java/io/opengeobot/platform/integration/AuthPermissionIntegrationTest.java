/*
 * Function: Real auth and permission integration tests - login, JWT, RBAC enforcement
 * Time: 2026-07-09
 * Author: AxeXie
 */
package io.opengeobot.platform.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real integration tests for authentication and permission enforcement backed
 * by a PostgreSQL container. Tests the full JWT flow: login with real
 * credentials, token validation via {@code JwtAuthenticationFilter}, and RBAC
 * enforcement via {@code @PreAuthorize} on controllers.
 *
 * <p>The seeded admin user (username: {@code admin}, password: {@code admin123})
 * has the {@code SYS_ADMIN} role which carries all permissions seeded in V4.
 * The admin therefore has IAM/governance permissions (e.g.
 * {@code platform.role.read}) but NOT robot/mission/safety permissions (e.g.
 * {@code robot.robot.read}) because those are not seeded in any migration.
 *
 * <p>{@code @Transactional} ensures each test method runs in a transaction that
 * is rolled back at the end, cleaning up session and token records.
 */
@Transactional
class AuthPermissionIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "password";

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode tokenNode = body.get("access_token");
        assertNotNull(tokenNode, "Login response should contain access_token field");
        return tokenNode.asText();
    }

    @Test
    void login_validCredentialsReturnsJwtToken() throws Exception {
        String token = loginAndGetToken(ADMIN_USERNAME, ADMIN_PASSWORD);

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertTrue(token.contains("."), "JWT should contain two dots separating header.payload.signature");
    }

    @Test
    void login_invalidCredentialsReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unknownUserReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nonexistent\",\"password\":\"anypassword\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessProtectedEndpoint_withValidTokenReturns200() throws Exception {
        String token = loginAndGetToken(ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void accessProtectedEndpoint_withoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessProtectedEndpoint_withoutRequiredPermissionReturns403() throws Exception {
        String token = loginAndGetToken(ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/v1/robots")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessProtectedEndpoint_withInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_createsSessionAndAuditRecords() throws Exception {
        String token = loginAndGetToken(ADMIN_USERNAME, ADMIN_PASSWORD);

        assertNotNull(token);

        Long sessionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_iam.sys_session WHERE user_id = ? AND state = ?",
                Long.class, "usr_01J00000000000000000000001", "ACTIVE");
        assertNotNull(sessionCount);
        assertTrue(sessionCount >= 1, "An active session should be created on login");

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_governance.sys_operation_audit WHERE actor_id = ? AND action = ? AND result = ?",
                Long.class, "usr_01J00000000000000000000001", "LOGIN", "SUCCESS");
        assertNotNull(auditCount);
        assertTrue(auditCount >= 1, "A successful LOGIN audit record should be created");

        Long outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_governance.outbox_event WHERE aggregate_type = ? AND event_type = ?",
                Long.class, "user", "iam.user_logged_in.v1");
        assertNotNull(outboxCount);
        assertTrue(outboxCount >= 1, "iam.user_logged_in.v1 outbox event should be created");
    }
}
