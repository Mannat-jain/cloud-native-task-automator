package com.example.task.service;

import java.time.Duration;

/** Exponential backoff: base, 2*base, 4*base, ... capped at {@code max}. */
public class RetryPolicy {

    private final Duration base;
    private final Duration max;

    public RetryPolicy(Duration base, Duration max) {
        if (base.isNegative() || base.isZero() || max.compareTo(base) < 0) {
            throw new IllegalArgumentException("need 0 < base <= max");
        }
        this.base = base;
        this.max = max;
    }

    /** @param attempts how many attempts have been made so far (>= 1) */
    public Duration delayAfterAttempt(int attempts) {
        int exponent = Math.min(Math.max(attempts - 1, 0), 20);          // 2^20 keeps the long math safe
        long millis = base.toMillis() * (1L << exponent);
        return Duration.ofMillis(Math.min(millis, max.toMillis()));
    }
}
