/*
 * Function: IAM module configuration marker — enables component scanning for the IAM domain
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.config;

import org.springframework.context.annotation.Configuration;

/**
 * Marker {@link Configuration} for the platform IAM module.
 * Ensures the IAM package tree is included in component scanning.
 */
@Configuration
public class IamModuleConfig {
}
