package br.com.portfolio.webhook.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Minimal distributed lock built on {@code SET key value NX EX ttl}.
 *
 * <p>Why: the dispatcher runs on every instance. Without a lock, two instances
 * could poll the same due rows concurrently. The TTL is the safety net — if a
 * worker dies holding the lock, it expires on its own.
 *
 * <p>Honest limitation: release does not verify ownership, so a worker whose lock
 * already expired could release a successor's lock. For a portfolio service this
 * is an accepted trade-off; a production system would use a fencing token
 * (Redisson/Redlock). The CAS claim in {@link DispatchService} is the real
 * guarantee against double delivery, so the lock only reduces wasted work.
 */
@Service
@RequiredArgsConstructor
public class RedisLockService {

    private final StringRedisTemplate redis;

    public boolean tryLock(String key, Duration ttl) {
        Boolean acquired = redis.opsForValue().setIfAbsent(key, Long.toString(System.nanoTime()), ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public void release(String key) {
        redis.delete(key);
    }
}
