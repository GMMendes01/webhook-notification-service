package br.com.portfolio.webhook.dto;

import br.com.portfolio.webhook.model.Delivery;
import br.com.portfolio.webhook.model.enums.DeliveryChannel;
import br.com.portfolio.webhook.model.enums.DeliveryStatus;
import java.time.Instant;
import java.util.UUID;

public record DeliveryResponse(
        UUID id,
        UUID eventId,
        UUID subscriptionId,
        DeliveryChannel channel,
        String target,
        DeliveryStatus status,
        int attemptCount,
        int maxAttempts,
        Instant nextAttemptAt,
        Integer lastStatusCode,
        String lastError,
        Long responseMs,
        Instant updatedAt
) {
    public static DeliveryResponse from(Delivery d) {
        return new DeliveryResponse(
                d.getId(),
                d.getEvent().getId(),
                d.getSubscription().getId(),
                d.getChannel(),
                d.getTarget(),
                d.getStatus(),
                d.getAttemptCount(),
                d.getMaxAttempts(),
                d.getNextAttemptAt(),
                d.getLastStatusCode(),
                d.getLastError(),
                d.getResponseMs(),
                d.getUpdatedAt()
        );
    }
}
