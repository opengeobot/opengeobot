/*
 * Function: Health controller integration tests — verifies live and info endpoints
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.bootstrap.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link HealthController} using the test profile
 * (H2 in-memory database, Flyway disabled).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void live_returns200AndHealthyState() throws Exception {
        mockMvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("HEALTHY"))
                .andExpect(jsonPath("$.service_name").value("cloud-control"));
    }

    @Test
    void info_returnsVersionInfo() throws Exception {
        mockMvc.perform(get("/health/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service_name").value("cloud-control"))
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.java_version").value(21));
    }
}
