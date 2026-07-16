/*
 * Function: Skill list NATS responder - serves registered skill definitions
 * Time: 2026-07-16
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.nats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.opengeobot.platform.common.event.NatsConnectionManager;
import io.opengeobot.platform.robot.dto.SkillDto;
import io.opengeobot.platform.robot.service.SkillService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Responds to NATS request-reply on {@code opengeobot.skill.list} with a JSON
 * array of all PUBLISHED skill definitions.
 * <p>
 * The agent-runtime (QwenPaw) queries this subject to validate that each
 * {@code skill_id} in an LLM-generated plan refers to a registered skill and
 * that the step {@code params} conform to the skill's {@code input_schema}.
 * Without this responder the subject has no listener, so every skill lookup
 * times out and validation always fails.
 * <p>
 * The response is a JSON array where each element contains:
 * <pre>
 * { "skill_id": "...", "name": "...", "input_schema": {...}, "description": "..." }
 * </pre>
 * <p>
 * Only instantiated when {@code opengeobot.nats.enabled=true} (the default).
 */
@Component
@ConditionalOnProperty(prefix = "opengeobot.nats", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SkillListNatsResponder {

    private static final Logger log = LoggerFactory.getLogger(SkillListNatsResponder.class);
    private static final String SKILL_LIST_SUBJECT = "opengeobot.skill.list";

    private final NatsConnectionManager connectionManager;
    private final ObjectMapper objectMapper;
    private final SkillService skillService;

    private volatile Dispatcher dispatcher;

    public SkillListNatsResponder(NatsConnectionManager connectionManager,
                                  ObjectMapper objectMapper,
                                  SkillService skillService) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
        this.skillService = skillService;
    }

    @PostConstruct
    public void init() {
        try {
            if (!connectionManager.isConnected()) {
                boolean connected = connectionManager.tryConnect();
                if (!connected) {
                    log.warn("NATS not connected at startup; skill list responder will not subscribe. " +
                            "It will retry on reconnection events.");
                    return;
                }
            }

            Connection connection = connectionManager.getConnection();
            if (connection == null) {
                log.warn("NATS connection is null; skill list responder will not subscribe");
                return;
            }

            dispatcher = connection.createDispatcher(this::onMessage);
            dispatcher.subscribe(SKILL_LIST_SUBJECT);
            log.info("Subscribed to skill list requests on '{}'", SKILL_LIST_SUBJECT);
        } catch (Exception e) {
            log.warn("Failed to subscribe to skill list requests: {}", e.getMessage());
        }
    }

    /**
     * NATS message handler. Queries all PUBLISHED skills and publishes the
     * JSON array response on the request's reply subject.
     */
    void onMessage(Message msg) {
        String replyTo = msg.getReplyTo();
        if (replyTo == null || replyTo.isBlank()) {
            log.debug("Skill list request has no reply subject; ignoring");
            return;
        }

        try {
            List<SkillDto> skills = skillService.listPublishedSkills();
            byte[] payload = buildSkillListJson(skills);

            Connection connection = connectionManager.getConnection();
            if (connection == null) {
                log.warn("NATS connection lost; cannot respond to skill list request");
                return;
            }
            connection.publish(replyTo, payload);
            log.debug("Responded to skill list request with {} published skill(s)", skills.size());
        } catch (Exception e) {
            log.error("Failed to handle skill list request: {}", e.getMessage(), e);
            publishErrorResponse(replyTo, e.getMessage());
        }
    }

    /**
     * Builds the JSON array of skill definitions. Each element contains
     * {@code skill_id}, {@code name}, {@code input_schema} (parsed JSON object)
     * and {@code description}.
     */
    byte[] buildSkillListJson(List<SkillDto> skills) throws Exception {
        ArrayNode array = objectMapper.createArrayNode();
        for (SkillDto skill : skills) {
            ObjectNode node = array.addObject();
            node.put("skill_id", skill.skillId());
            node.put("name", skill.name());
            node.put("description", skill.description() != null ? skill.description() : "");
            node.set("input_schema", parseSchema(skill.inputSchema()));
        }
        return objectMapper.writeValueAsBytes(array);
    }

    /**
     * Parses a JSON Schema string into a {@link JsonNode}. Falls back to an
     * empty object if the string is blank or not valid JSON.
     */
    private JsonNode parseSchema(String inputSchema) {
        if (inputSchema == null || inputSchema.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(inputSchema);
        } catch (Exception e) {
            log.warn("Failed to parse input_schema as JSON for skill; returning empty object: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private void publishErrorResponse(String replyTo, String errorMessage) {
        try {
            Connection connection = connectionManager.getConnection();
            if (connection == null) {
                return;
            }
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", errorMessage);
            error.putArray("skills");
            connection.publish(replyTo, objectMapper.writeValueAsBytes(error));
        } catch (Exception e) {
            log.warn("Failed to publish error response for skill list request: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        if (dispatcher != null) {
            try {
                Connection connection = connectionManager.getConnection();
                if (connection != null) {
                    connection.closeDispatcher(dispatcher);
                    log.info("Unsubscribed from skill list requests");
                }
            } catch (Exception e) {
                log.warn("Error closing skill list dispatcher: {}", e.getMessage());
            }
        }
    }
}
