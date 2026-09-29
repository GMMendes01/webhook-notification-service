package br.com.portfolio.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetryBackoffPolicyTest {

    private final RetryBackoffPolicy policy =
            new RetryBackoffPolicy(Duration.ofSeconds(5), Duration.ofSeconds(900), 5);

    @Test
    @DisplayName("delay doubles on each attempt: 5s, 10s, 20s, 40s")
    void doublesEachAttempt() {
        assertThat(policy.delayAfter(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.delayAfter(2)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.delayAfter(3)).isEqualTo(Duration.ofSeconds(20));
        assertThat(policy.delayAfter(4)).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    @DisplayName("delay is capped at maxDelay")
    void capsAtMax() {
        // 5s * 2^7 = 640s, still under the 900s cap.
        assertThat(policy.delayAfter(8)).isEqualTo(Duration.ofSeconds(640));
        // 5s * 2^8 = 1280s -> clamped to 900s.
        assertThat(policy.delayAfter(9)).isEqualTo(Duration.ofSeconds(900));
        assertThat(policy.delayAfter(20)).isEqualTo(Duration.ofSeconds(900));
        assertThat(policy.delayAfter(1000)).isEqualTo(Duration.ofSeconds(900));
    }

    @Test
    @DisplayName("attempts at or beyond maxAttempts are exhausted")
    void exhaustion() {
        assertThat(policy.isExhausted(1)).isFalse();
        assertThat(policy.isExhausted(4)).isFalse();
        assertThat(policy.isExhausted(5)).isTrue();
        assertThat(policy.isExhausted(6)).isTrue();
    }

    @Test
    @DisplayName("rejects attempt < 1")
    void rejectsInvalidAttempt() {
        assertThatThrownBy(() -> policy.delayAfter(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects maxAttempts < 1")
    void rejectsInvalidMaxAttempts() {
        assertThatThrownBy(() -> new RetryBackoffPolicy(Duration.ofSeconds(1), Duration.ofSeconds(2), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
