/*
 * Function: WebSocket configuration — registers /ws/monitor endpoint for F-MONITOR-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.robot.monitor.MonitorWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the {@code /ws/monitor} WebSocket endpoint and wires it to the
 * {@link MonitorWebSocketHandler}. Allowed origins are restricted to the
 * frontend dev server ({@code http://localhost:5173}); production origins are
 * configured via {@code app.cors.allowed-origins} where applicable. Scheduling
 * is enabled here to support the {@link io.opengeobot.platform.robot.monitor.MonitorEventPublisher}
 * polling fallback.
 */
@Configuration
@EnableWebSocket
@EnableScheduling
public class WebSocketConfig implements WebSocketConfigurer {

    private static final String MONITOR_ENDPOINT = "/ws/monitor";
    private static final String[] ALLOWED_ORIGINS = {"http://localhost:5173"};

    private final ObjectMapper objectMapper;

    public WebSocketConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public MonitorWebSocketHandler monitorWebSocketHandler() {
        return new MonitorWebSocketHandler(objectMapper);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(monitorWebSocketHandler(), MONITOR_ENDPOINT)
                .setAllowedOrigins(ALLOWED_ORIGINS);
    }
}
