package org.workfitai.cvservice.config;

import java.util.concurrent.Executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@code @Async} and provides bounded executors for background CV section
 * enrichment (Ollama re-extraction).
 *
 * Two separate pools, on purpose: {@code cvEnrichmentExecutor} runs the actual
 * per-CV Ollama round-trip (up to 60s each) triggered by real-time uploads AND by
 * the startup backfill's individual enrich calls; {@code cvBackfillExecutor} runs
 * only the backfill's own orchestration loop (sequential MinIO downloads + PDF text
 * extraction). Sharing one pool for both would let a restart's backfill pass — which
 * blocks a core thread for its whole batch — starve or drop real uploads' enrichment
 * for the duration. Giving the orchestration loop its own single thread keeps that
 * loop off the enrichment pool while it still funnels the actual Ollama calls onto
 * the same bounded {@code cvEnrichmentExecutor} as everything else.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "cvEnrichmentExecutor")
    public Executor cvEnrichmentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("cv-enrich-");
        // Drop-and-log on saturation: keep the enrichment OFF the request thread.
        executor.setRejectedExecutionHandler((r, exec) ->
                log.warn("cv-enrichment executor saturated — dropping enrichment task (best-effort, heuristic sections kept)"));
        executor.initialize();
        return executor;
    }

    @Bean(name = "cvBackfillExecutor")
    public Executor cvBackfillExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("cv-backfill-");
        executor.initialize();
        return executor;
    }
}
