/*
 * Function: Alarm evaluator — scheduled task for periodic alarm rule evaluation
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically evaluates all enabled alarm rules and triggers alarms for
 * conditions that are met. Runs every 30 seconds via Spring's
 * {@code @Scheduled} support (scheduling is enabled centrally via
 * {@code @EnableScheduling} on {@code WebSocketConfig}).
 *
 * <p>For M5, the evaluation is a simple check of robot offline duration and
 * mission failure rate against configured thresholds. In later milestones
 * this can be extended to support additional metrics and Prometheus-based
 * evaluation.</p>
 */
@Component
public class AlarmEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AlarmEvaluator.class);

    private final AlarmService alarmService;

    public AlarmEvaluator(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void evaluateRules() {
        try {
            alarmService.evaluateRules();
        } catch (Exception e) {
            log.error("Alarm rule evaluation failed: {}", e.getMessage(), e);
        }
    }
}
