package com.example.task.repository;

import com.example.task.model.Task;
import com.example.task.model.TaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/** Persistence only - no business rules live here. */
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByStatus(TaskStatus status, Pageable pageable);

    long countByStatus(TaskStatus status);

    /** Ids of tasks that are due to run, oldest first. */
    @Query("select t.id from Task t where t.status = :status and t.nextRunAt <= :now "
            + "order by t.nextRunAt asc, t.id asc")
    List<Long> findDueIds(@Param("status") TaskStatus status, @Param("now") Instant now, Pageable pageable);

    /**
     * Atomically claims a task: PENDING -> RUNNING and attempts + 1 in ONE conditional UPDATE.
     * Because the status check and the change happen in a single statement, two workers (or two
     * application instances) racing for the same task cannot both win - exactly one gets 1, the other 0.
     * There is no read-then-write window, so no double execution.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Task t set t.status = :to, t.startedAt = :now, t.updatedAt = :now, "
            + "t.attempts = t.attempts + 1, t.version = t.version + 1 "
            + "where t.id = :id and t.status = :from")
    int claim(@Param("id") Long id, @Param("from") TaskStatus from, @Param("to") TaskStatus to,
              @Param("now") Instant now);

    /** A worker that died leaves tasks RUNNING forever; put those that still have attempts left back in the queue. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Task t set t.status = :to, t.nextRunAt = :now, t.updatedAt = :now, t.lastError = :reason, "
            + "t.version = t.version + 1 "
            + "where t.status = :from and t.startedAt < :cutoff and t.attempts < t.maxAttempts")
    int requeueStale(@Param("from") TaskStatus from, @Param("to") TaskStatus to, @Param("cutoff") Instant cutoff,
                     @Param("now") Instant now, @Param("reason") String reason);

    /** ...and fail those that have no attempts left. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Task t set t.status = :to, t.finishedAt = :now, t.updatedAt = :now, t.nextRunAt = null, "
            + "t.lastError = :reason, t.version = t.version + 1 "
            + "where t.status = :from and t.startedAt < :cutoff and t.attempts >= t.maxAttempts")
    int failStale(@Param("from") TaskStatus from, @Param("to") TaskStatus to, @Param("cutoff") Instant cutoff,
                  @Param("now") Instant now, @Param("reason") String reason);
}
