package br.com.portfolio.webhook.dto;

import br.com.portfolio.webhook.model.Event;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String eventType,
        String payload,
        String idempotencyKey,
        Instant receivedAt,
        List<DeliveryResponse> deliveries
) {
    public static EventResponse from(Event e, List<DeliveryResponse> deliveries) {
        return new EventResponse(e.getId(), e.getEventType(), e.getPayload(),
                e.getIdempotencyKey(), e.getReceivedAt(), deliveries);
    }
}
