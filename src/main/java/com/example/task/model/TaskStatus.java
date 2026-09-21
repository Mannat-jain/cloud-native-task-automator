package com.example.task.model;

/**
 * Lifecycle of a task.
 *
 * <pre>
 *   PENDING --claim--> RUNNING --success--> SUCCEEDED
 *      ^                  |
 *      |   (attempts left)|--failure--> PENDING (next_run_at = now + exponential backoff)
 *      |                  |
 *      |                  `--failure, no attempts left--> FAILED
 *      `-- stale RUNNING task (worker died) is requeued, or FAILED if it has no attempts left
 * </pre>
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED
}
