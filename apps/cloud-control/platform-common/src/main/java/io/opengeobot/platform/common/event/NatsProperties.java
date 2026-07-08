/*
 * Function: NATS / JetStream configuration properties — binds opengeobot.nats.*
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.common.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * NATS and JetStream configuration bound from {@code opengeobot.nats.*} properties.
 * The URL is injected via the {@code NATS_URL} environment variable in Docker
 * deployments. When JetStream is enabled, the relay publishes outbox events to
 * a JetStream stream whose name is configurable.
 *
 * @param url              NATS server URL
 * @param username         optional username for NATS authentication
 * @param password         optional password for NATS authentication
 * @param credentialsFile  optional path to a NATS credentials file
 * @param connectionTimeout timeout for establishing the initial connection
 * @param reconnectWait    delay between reconnection attempts
 * @param maxReconnects    maximum reconnection attempts (-1 = unlimited)
 * @param enabled          whether NATS integration is enabled (default true)
 * @param jetStream        JetStream-specific configuration
 * @param outbox           Outbox Relay configuration
 */
@ConfigurationProperties(prefix = "opengeobot.nats")
public class NatsProperties {

    private String url = "nats://localhost:4222";
    private String username;
    private String password;
    private String credentialsFile;
    private Duration connectionTimeout = Duration.ofSeconds(5);
    private Duration reconnectWait = Duration.ofSeconds(2);
    private int maxReconnects = -1;
    private boolean enabled = true;
    private JetStream jetStream = new JetStream();
    private Outbox outbox = new Outbox();

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCredentialsFile() {
        return credentialsFile;
    }

    public void setCredentialsFile(String credentialsFile) {
        this.credentialsFile = credentialsFile;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Duration getReconnectWait() {
        return reconnectWait;
    }

    public void setReconnectWait(Duration reconnectWait) {
        this.reconnectWait = reconnectWait;
    }

    public int getMaxReconnects() {
        return maxReconnects;
    }

    public void setMaxReconnects(int maxReconnects) {
        this.maxReconnects = maxReconnects;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public JetStream getJetStream() {
        return jetStream;
    }

    public void setJetStream(JetStream jetStream) {
        this.jetStream = jetStream;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(Outbox outbox) {
        this.outbox = outbox;
    }

    /**
     * JetStream-specific configuration.
     */
    public static class JetStream {
        private boolean enabled = true;
        private String stream = "opengeobot-events";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getStream() {
            return stream;
        }

        public void setStream(String stream) {
            this.stream = stream;
        }
    }

    /**
     * Outbox Relay configuration.
     */
    public static class Outbox {
        private boolean relayEnabled = true;
        private long relayIntervalMs = 5000;
        private int batchSize = 100;
        private int maxRetryCount = 50;
        private long retryBackoffMs = 10000;

        public boolean isRelayEnabled() {
            return relayEnabled;
        }

        public void setRelayEnabled(boolean relayEnabled) {
            this.relayEnabled = relayEnabled;
        }

        public long getRelayIntervalMs() {
            return relayIntervalMs;
        }

        public void setRelayIntervalMs(long relayIntervalMs) {
            this.relayIntervalMs = relayIntervalMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxRetryCount() {
            return maxRetryCount;
        }

        public void setMaxRetryCount(int maxRetryCount) {
            this.maxRetryCount = maxRetryCount;
        }

        public long getRetryBackoffMs() {
            return retryBackoffMs;
        }

        public void setRetryBackoffMs(long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
        }
    }
}
