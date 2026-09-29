package br.com.portfolio.webhook.service;

import java.time.Duration;

/**
 * Pure exponential backoff: {@code delay = base * 2^(attempt-1)}, capped at {@code max}.
 *
 * <p>Kept free of Spring/JPA so it can be unit-tested as a plain function.
 * Example with base=5s, max=15min: 5s, 10s, 20s, 40s, 1m20s, ..., 15m, 15m...
 */
public class RetryBackoffPolicy {

    private final Duration baseDelay;
    private final Duration maxDelay;
    private final int maxAttempts;

    public RetryBackoffPolicy(Duration baseDelay, Duration maxDelay, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Delay before the next attempt.
     *
     * @param attempt just-recorded attempt count (1 = first failure)
     */
    public Duration delayAfter(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1");
        }
        int exponent = Math.min(attempt - 1, 30); // guard against overflow
        long multiplier = 1L << exponent;
        Duration raw;
        try {
            raw = baseDelay.multipliedBy(multiplier);
        } catch (ArithmeticException e) {
            return maxDelay;
        }
        return raw.compareTo(maxDelay) > 0 ? maxDelay : raw;
    }

    public boolean isExhausted(int attempt) {
        return attempt >= maxAttempts;
    }

    public int maxAttempts() {
        return maxAttempts;
    }
}
