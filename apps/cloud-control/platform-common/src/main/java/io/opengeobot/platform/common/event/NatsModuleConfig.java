/*
 * Function: NATS module configuration — enables NATS properties binding
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.common.event;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Marker {@link Configuration} for the NATS integration in platform-common.
 * Binds the {@link NatsProperties} configuration so that the connection manager
 * and outbox relay can consume {@code opengeobot.nats.*} properties.
 */
@Configuration
@EnableConfigurationProperties(NatsProperties.class)
public class NatsModuleConfig {
}
