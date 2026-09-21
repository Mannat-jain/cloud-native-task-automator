package com.example.task.config;

import com.example.task.service.RetryPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AppConfig {

    /** Injectable clock so time-dependent logic (backoff, stale detection) is testable. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public RetryPolicy retryPolicy(@Value("${app.retry.base-delay-ms:2000}") long baseDelayMs,
                                   @Value("${app.retry.max-delay-ms:300000}") long maxDelayMs) {
        return new RetryPolicy(Duration.ofMillis(baseDelayMs), Duration.ofMillis(maxDelayMs));
    }

    /**
     * Bounded worker pool: fixed threads + bounded queue. When it is full the poller itself runs the task
     * (CallerRunsPolicy), which slows polling down - natural back-pressure instead of unbounded memory growth.
     */
    @Bean(name = "taskWorkerExecutor")
    public ThreadPoolTaskExecutor taskWorkerExecutor(@Value("${app.worker.threads:4}") int threads,
                                                     @Value("${app.worker.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("task-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
