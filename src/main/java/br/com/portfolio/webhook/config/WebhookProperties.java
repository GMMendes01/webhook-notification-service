package br.com.portfolio.webhook.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "webhook")
public class WebhookProperties {

    private final Dispatcher dispatcher = new Dispatcher();
    private final Retry retry = new Retry();
    private final Idempotency idempotency = new Idempotency();

    @Getter
    @Setter
    public static class Dispatcher {
        private boolean enabled = true;
        private long intervalMs = 2000;
        private int batchSize = 50;
        private long lockTtlSeconds = 30;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
    }

    @Getter
    @Setter
    public static class Retry {
        private int maxAttempts = 5;
        private long baseDelaySeconds = 5;
        private long maxDelaySeconds = 900;

        public Duration baseDelay() {
            return Duration.ofSeconds(baseDelaySeconds);
        }

        public Duration maxDelay() {
            return Duration.ofSeconds(maxDelaySeconds);
        }
    }

    @Getter
    @Setter
    public static class Idempotency {
        private long ttlHours = 24;

        public Duration ttl() {
            return Duration.ofHours(ttlHours);
        }
    }
}
