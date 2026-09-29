package br.com.portfolio.webhook.dto;

import br.com.portfolio.webhook.model.Subscription;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        String name,
        String webhookUrl,
        String secret,
        List<String> eventTypes,
        boolean emailNotify,
        String emailAddress,
        boolean active,
        Instant createdAt
) {
    public static SubscriptionResponse from(Subscription s) {
        return new SubscriptionResponse(
                s.getId(),
                s.getName(),
                s.getWebhookUrl(),
                s.getSecret(),
                s.getEventTypes() == null ? List.of() : List.of(s.getEventTypes()),
                s.isEmailNotify(),
                s.getEmailAddress(),
                s.isActive(),
                s.getCreatedAt()
        );
    }
}
