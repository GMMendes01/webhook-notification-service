package br.com.portfolio.webhook.worker;

import br.com.portfolio.webhook.config.WebhookProperties;
import br.com.portfolio.webhook.service.DispatchService;
import br.com.portfolio.webhook.service.RedisLockService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polling worker: every {@code webhook.dispatcher.interval-ms} it takes a Redis
 * lock (so only one instance dispatches at a time) and runs one dispatch pass.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "webhook.dispatcher.enabled", havingValue = "true", matchIfMissing = true)
public class DispatchWorker {

    private static final String LOCK_KEY = "webhook:dispatch-lock";

    private final DispatchService dispatchService;
    private final RedisLockService lockService;
    private final WebhookProperties props;

    @Scheduled(fixedDelayString = "${webhook.dispatcher.interval-ms:2000}")
    public void run() {
        Duration ttl = Duration.ofSeconds(props.getDispatcher().getLockTtlSeconds());
        if (!lockService.tryLock(LOCK_KEY, ttl)) {
            return; // another instance is dispatching
        }
        try {
            dispatchService.dispatchDueDeliveries();
        } catch (Exception e) {
            log.error("Dispatch pass failed", e);
        } finally {
            lockService.release(LOCK_KEY);
        }
    }
}
