/*
 * Function: Governance module configuration — async support for export tasks
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for the platform governance module. Provides an async executor
 * used by the export worker. Mapper scanning is handled centrally by
 * {@code CloudControlApplication.@MapperScan("io.opengeobot.platform.**.repository")}.
 */
@Configuration
@EnableAsync
public class GovernanceModuleConfig {

    /**
     * Dedicated executor for asynchronous export processing so that export
     * work does not block request threads.
     */
    @Bean(name = "exportExecutor")
    public Executor exportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("export-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
