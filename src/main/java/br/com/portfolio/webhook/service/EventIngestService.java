package br.com.portfolio.webhook.service;

import br.com.portfolio.webhook.config.WebhookProperties;
import br.com.portfolio.webhook.dto.EventAcceptedResponse;
import br.com.portfolio.webhook.model.Delivery;
import br.com.portfolio.webhook.model.Event;
import br.com.portfolio.webhook.model.Subscription;
import br.com.portfolio.webhook.model.enums.DeliveryChannel;
import br.com.portfolio.webhook.model.enums.DeliveryStatus;
import br.com.portfolio.webhook.repository.DeliveryRepository;
import br.com.portfolio.webhook.repository.EventRepository;
import br.com.portfolio.webhook.repository.SubscriptionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingest path. This is the "outbox" write: an event and all its deliveries are
 * committed in one transaction, and nothing leaves the process here. The
 * dispatcher worker delivers them later.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventIngestService {

    private final EventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DeliveryRepository deliveryRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final WebhookProperties props;
    private final RetryBackoffPolicy backoffPolicy;

    @Transactional
    public EventAcceptedResponse ingest(String eventType, Object payload, String idempotencyKey) {
        // Fast pre-check in Redis; the DB unique constraint below is the real source of truth.
        if (idempotencyKey != null) {
            Boolean firstSeen = redis.opsForValue().setIfAbsent(
                    idempotencyKey(idempotencyKey), "1", props.getIdempotency().ttl());
            if (Boolean.FALSE.equals(firstSeen)) {
                Optional<Event> existing = eventRepository.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) {
                    log.debug("Idempotent replay of key={} -> event {}", idempotencyKey, existing.get().getId());
                    return new EventAcceptedResponse(existing.get().getId(), 0, true);
                }
            }
        }

        Instant now = Instant.now();
        Event event = Event.builder()
                .eventType(eventType)
                .payload(serialize(payload))
                .idempotencyKey(idempotencyKey)
                .receivedAt(now)
                .build();
        try {
            event = eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            // Lost the race against a concurrent ingest with the same key.
            Event existing = eventRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> e);
            return new EventAcceptedResponse(existing.getId(), 0, true);
        }

        List<Delivery> deliveries = new ArrayList<>();
        for (Subscription subscription : subscriptionRepository.findByActiveTrue()) {
            if (!subscription.subscribesTo(eventType)) {
                continue;
            }
            deliveries.add(buildDelivery(event, subscription, DeliveryChannel.WEBHOOK,
                    subscription.getWebhookUrl(), now));
            if (subscription.isEmailNotify() && subscription.getEmailAddress() != null) {
                deliveries.add(buildDelivery(event, subscription, DeliveryChannel.EMAIL,
                        subscription.getEmailAddress(), now));
            }
        }
        deliveryRepository.saveAll(deliveries);

        log.info("Ingested event {} type={} -> {} deliveries", event.getId(), eventType, deliveries.size());
        return new EventAcceptedResponse(event.getId(), deliveries.size(), false);
    }

    private Delivery buildDelivery(Event event, Subscription subscription,
                                   DeliveryChannel channel, String target, Instant now) {
        return Delivery.builder()
                .event(event)
                .subscription(subscription)
                .channel(channel)
                .target(target)
                .status(DeliveryStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(backoffPolicy.maxAttempts())
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("payload is not serializable to JSON", e);
        }
    }

    private String idempotencyKey(String key) {
        return "idem:" + key;
    }

    @Transactional(readOnly = true)
    public Event findEvent(UUID id) {
        return eventRepository.findById(id).orElseThrow(
                () -> new br.com.portfolio.webhook.exception.NotFoundException("Event", id));
    }
}
