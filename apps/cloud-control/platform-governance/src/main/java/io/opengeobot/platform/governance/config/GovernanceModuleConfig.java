/*
 * Function: Governance module configuration marker — enables component scanning for the governance domain
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.config;

import org.springframework.context.annotation.Configuration;

/**
 * Marker {@link Configuration} for the platform governance module.
 * Ensures the governance package tree is included in component scanning.
 */
@Configuration
public class GovernanceModuleConfig {
}
