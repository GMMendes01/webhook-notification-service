package br.com.portfolio.webhook.service;

import br.com.portfolio.webhook.config.WebhookProperties;
import br.com.portfolio.webhook.dto.DeliveryOutcome;
import br.com.portfolio.webhook.exception.InvalidStateException;
import br.com.portfolio.webhook.exception.NotFoundException;
import br.com.portfolio.webhook.model.Delivery;
import br.com.portfolio.webhook.model.enums.DeliveryStatus;
import br.com.portfolio.webhook.repository.DeliveryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates one dispatch pass: pick up due rows, claim each with a CAS update,
 * hand it to the right channel, then record the outcome (retry or dead-letter).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DispatchService {

    private final DeliveryRepository deliveryRepository;
    private final WebhookDeliveryService webhookDeliveryService;
    private final EmailDeliveryService emailDeliveryService;
    private final RetryBackoffPolicy backoffPolicy;
    private final WebhookProperties props;
    private final MeterRegistry meterRegistry;

    /**
     * Self-reference to the Spring proxy. Without it, {@link #attemptOne(UUID)} would be a plain
     * self-invocation and its {@code @Transactional} (needed by the CAS claim) would never apply.
     */
    @Autowired
    @Lazy
    private DispatchService self;

    /**
     * Claims and processes one batch of due deliveries. Returns how many were attempted.
     * Each delivery is handled in its own transaction so one bad row cannot roll back the batch.
     */
    public int dispatchDueDeliveries() {
        Instant now = Instant.now();
        List<DeliveryStatus> statuses = List.of(DeliveryStatus.PENDING, DeliveryStatus.FAILED);
        List<UUID> candidates = deliveryRepository
                .findDue(statuses, now, org.springframework.data.domain.PageRequest.of(0, props.getDispatcher().getBatchSize()))
                .stream()
                .map(Delivery::getId)
                .toList();

        int attempted = 0;
        for (UUID id : candidates) {
            // via self so the @Transactional boundary on attemptOne actually applies
            if (self.attemptOne(id)) {
                attempted++;
            }
        }
        if (attempted > 0) {
            log.debug("Dispatch pass attempted {} deliveries", attempted);
        }
        return attempted;
    }

    /**
     * @return true when this worker won the CAS claim and actually delivered the row.
     */
    @Transactional
    public boolean attemptOne(UUID deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null) {
            return false;
        }
        if (delivery.getStatus().isTerminal()) {
            return false;
        }
        DeliveryStatus expected = delivery.getStatus();
        int expectedAttempt = delivery.getAttemptCount();

        int claimed = deliveryRepository.claim(
                deliveryId, expected, expectedAttempt, DeliveryStatus.DELIVERING, Instant.now());
        if (claimed == 0) {
            return false; // another worker got it first
        }

        // claim() clears the persistence context, so `delivery` is detached and its lazy
        // associations are gone. Re-read with event + subscription fetched in one query.
        Delivery claimedDelivery = deliveryRepository.findWithGraphById(deliveryId).orElse(null);
        if (claimedDelivery == null) {
            return false;
        }

        DeliveryOutcome outcome = switch (claimedDelivery.getChannel()) {
            case WEBHOOK -> webhookDeliveryService.deliver(claimedDelivery);
            case EMAIL -> emailDeliveryService.deliver(claimedDelivery);
        };

        recordOutcome(deliveryId, outcome);
        return true;
    }

    /** Runs inside {@link #attemptOne(UUID)}'s transaction (self-invocation would bypass a proxy anyway). */
    private void recordOutcome(UUID deliveryId, DeliveryOutcome outcome) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new NotFoundException("Delivery", deliveryId));

        int attempt = delivery.getAttemptCount() + 1;
        delivery.setAttemptCount(attempt);
        delivery.setLastStatusCode(outcome.statusCode());
        delivery.setLastError(outcome.error());
        delivery.setResponseMs(outcome.elapsedMs());
        delivery.setUpdatedAt(Instant.now());

        if (outcome.success()) {
            delivery.setStatus(DeliveryStatus.SUCCESS);
        } else if (backoffPolicy.isExhausted(attempt)) {
            delivery.setStatus(DeliveryStatus.DEAD_LETTER);
            log.warn("Delivery {} exhausted {} attempts -> DEAD_LETTER (last error: {})",
                    deliveryId, attempt, outcome.error());
        } else {
            Duration delay = backoffPolicy.delayAfter(attempt);
            delivery.setStatus(DeliveryStatus.FAILED);
            delivery.setNextAttemptAt(Instant.now().plus(delay));
            log.debug("Delivery {} failed (attempt {}), retrying in {}", deliveryId, attempt, delay);
        }
        deliveryRepository.save(delivery);

        meterRegistry.counter("webhook.deliveries",
                "channel", delivery.getChannel().name(),
                "status", delivery.getStatus().name()).increment();
        Timer.builder("webhook.delivery.latency")
                .tag("channel", delivery.getChannel().name())
                .register(meterRegistry)
                .record(outcome.elapsedMs(), TimeUnit.MILLISECONDS);
    }

    /** Re-queues a dead-lettered delivery. */
    @Transactional
    public Delivery replay(UUID deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new NotFoundException("Delivery", deliveryId));
        if (delivery.getStatus() != DeliveryStatus.DEAD_LETTER) {
            throw new InvalidStateException(
                    "Only DEAD_LETTER deliveries can be replayed; current status is " + delivery.getStatus());
        }
        delivery.setStatus(DeliveryStatus.PENDING);
        delivery.setAttemptCount(0);
        delivery.setNextAttemptAt(Instant.now());
        delivery.setLastError(null);
        delivery.setLastStatusCode(null);
        delivery.setUpdatedAt(Instant.now());
        return deliveryRepository.save(delivery);
    }
}
