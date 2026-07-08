/*
 * Function: NATS connection manager — lifecycle, reconnection, JetStream stream provisioning
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.common.event;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.api.StorageType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Manages the NATS {@link Connection} lifecycle including automatic reconnection
 * and JetStream stream provisioning. The connection is established lazily on
 * first access or via {@link #tryConnect()}; if NATS is unavailable at startup,
 * the manager logs a warning and retries on subsequent calls.
 * <p>
 * The manager is only instantiated when {@code opengeobot.nats.enabled=true}
 * (the default), so integration tests that do not require NATS can disable it
 * via the test profile.
 */
@Component
@ConditionalOnProperty(prefix = "opengeobot.nats", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NatsConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(NatsConnectionManager.class);

    private final NatsProperties properties;

    private volatile Connection connection;
    private volatile JetStream jetStream;
    private volatile boolean connected = false;
    private volatile boolean streamEnsured = false;

    public NatsConnectionManager(NatsProperties properties) {
        this.properties = properties;
    }

    /**
     * Attempts an initial connection on startup. Failures are logged but do not
     * prevent the application from starting — the relay will retry via
     * {@link #tryConnect()} on each polling cycle.
     */
    @PostConstruct
    public void init() {
        tryConnect();
    }

    /**
     * Tries to establish a NATS connection if one does not already exist or the
     * existing connection has been lost. This method is safe to call repeatedly.
     *
     * @return {@code true} if the connection is currently usable
     */
    public synchronized boolean tryConnect() {
        if (connected && connection != null) {
            return true;
        }
        try {
            Options.Builder builder = Options.builder()
                    .server(properties.getUrl())
                    .connectionTimeout(properties.getConnectionTimeout())
                    .reconnectWait(properties.getReconnectWait())
                    .maxReconnects(properties.getMaxReconnects())
                    .connectionListener(this::handleConnectionEvent);

            if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
                builder.userInfo(properties.getUsername(), properties.getPassword());
            }
            if (properties.getCredentialsFile() != null && !properties.getCredentialsFile().isBlank()) {
                builder.credentialPath(properties.getCredentialsFile());
            }

            connection = Nats.connect(builder.build());
            connected = connection.getStatus() == Connection.Status.CONNECTED;
            if (connected) {
                log.info("Connected to NATS at {}", properties.getUrl());
                jetStream = connection.jetStream();
                ensureStreamExists();
            }
        } catch (IOException | InterruptedException e) {
            log.warn("Failed to connect to NATS at {}: {}", properties.getUrl(), e.getMessage());
            connected = false;
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Unexpected error connecting to NATS at {}: {}", properties.getUrl(), e.getMessage());
            connected = false;
        }
        return connected;
    }

    private void handleConnectionEvent(Connection conn, ConnectionListener.Events event) {
        switch (event) {
            case CONNECTED, RECONNECTED -> {
                connected = true;
                log.info("NATS connection event: {}", event);
                try {
                    jetStream = conn.jetStream();
                    streamEnsured = false;
                    ensureStreamExists();
                } catch (Exception e) {
                    log.warn("Failed to obtain JetStream after reconnection: {}", e.getMessage());
                }
            }
            case DISCONNECTED -> {
                connected = false;
                log.warn("NATS connection lost, will attempt automatic reconnection");
            }
            case CLOSED -> {
                connected = false;
                log.warn("NATS connection closed");
            }
            default -> log.debug("NATS connection event: {}", event);
        }
    }

    /**
     * Creates the JetStream stream if it does not already exist. The stream
     * captures all subjects under {@code opengeobot.events.>}. Failures are
     * logged but do not prevent the relay from attempting to publish — the
     * server may auto-create subjects depending on configuration.
     */
    private synchronized void ensureStreamExists() {
        if (streamEnsured || connection == null) {
            return;
        }
        if (!properties.getJetStream().isEnabled()) {
            streamEnsured = true;
            return;
        }
        String streamName = properties.getJetStream().getStream();
        try {
            JetStreamManagement jsm = connection.jetStreamManagement();
            try {
                StreamInfo info = jsm.getStreamInfo(streamName);
                log.debug("JetStream stream '{}' already exists (subjects={})",
                        streamName, info.getConfiguration().getSubjects());
            } catch (Exception e) {
                StreamConfiguration sc = StreamConfiguration.builder()
                        .name(streamName)
                        .subjects("opengeobot.events.>")
                        .storageType(StorageType.File)
                        .build();
                jsm.addStream(sc);
                log.info("Created JetStream stream '{}' for subject pattern 'opengeobot.events.>'", streamName);
            }
            streamEnsured = true;
        } catch (Exception e) {
            log.warn("Could not ensure JetStream stream '{}' exists: {}", streamName, e.getMessage());
        }
    }

    /**
     * Returns the underlying NATS {@link Connection}, or {@code null} if NATS is
     * not connected. Callers should check {@link #isConnected()} first. This is
     * intended for core NATS operations such as request-reply that are not
     * available through JetStream.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Returns the current JetStream instance, or {@code null} if NATS is not
     * connected. Callers should check {@link #isConnected()} first.
     */
    public JetStream getJetStream() {
        return jetStream;
    }

    /**
     * Returns whether the NATS connection is currently established.
     */
    public boolean isConnected() {
        return connected && connection != null
                && connection.getStatus() == Connection.Status.CONNECTED;
    }

    /**
     * Returns the NATS connection status as a human-readable string for health
     * reporting.
     */
    public String getStatus() {
        if (connection == null) {
            return "NOT_CONNECTED";
        }
        return connection.getStatus().name();
    }

    @PreDestroy
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                log.info("NATS connection closed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while closing NATS connection");
            } catch (Exception e) {
                log.warn("Error closing NATS connection: {}", e.getMessage());
            }
        }
    }
}
