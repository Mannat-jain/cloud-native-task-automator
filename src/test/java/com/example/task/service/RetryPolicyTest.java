package com.example.task.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(Duration.ofSeconds(1), Duration.ofSeconds(10));

    @Test
    void backoffDoublesEveryAttempt() {
        assertEquals(Duration.ofSeconds(1), policy.delayAfterAttempt(1));
        assertEquals(Duration.ofSeconds(2), policy.delayAfterAttempt(2));
        assertEquals(Duration.ofSeconds(4), policy.delayAfterAttempt(3));
        assertEquals(Duration.ofSeconds(8), policy.delayAfterAttempt(4));
    }

    @Test
    void backoffIsCappedAndNeverOverflows() {
        assertEquals(Duration.ofSeconds(10), policy.delayAfterAttempt(5));
        assertEquals(Duration.ofSeconds(10), policy.delayAfterAttempt(500));
    }

    @Test
    void rejectsNonsenseConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(Duration.ZERO, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(Duration.ofSeconds(5), Duration.ofSeconds(1)));
    }
}
