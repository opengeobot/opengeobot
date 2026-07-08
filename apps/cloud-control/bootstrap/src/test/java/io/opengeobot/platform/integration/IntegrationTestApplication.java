/*
 * Function: Test configuration for integration tests in the platform.integration package
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.integration;

import io.opengeobot.bootstrap.CloudControlApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Bridges the package gap between the integration test package
 * ({@code io.opengeobot.platform.integration}) and the main application
 * configuration in {@code io.opengeobot.bootstrap}. Without this class,
 * {@code @SpringBootTest} cannot discover {@link CloudControlApplication}
 * because it searches the test class's package hierarchy.
 */
@SpringBootConfiguration
@Import(CloudControlApplication.class)
class IntegrationTestApplication {
}
