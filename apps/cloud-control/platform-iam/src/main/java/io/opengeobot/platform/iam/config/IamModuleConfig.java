/*
 * Function: IAM module configuration — enables component scanning and JWT properties
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.config;

import io.opengeobot.platform.iam.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Marker {@link Configuration} for the platform IAM module. Ensures the IAM
 * package tree is included in component scanning and binds the JWT
 * configuration properties.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class IamModuleConfig {
}
