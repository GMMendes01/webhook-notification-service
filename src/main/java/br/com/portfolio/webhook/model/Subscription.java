package br.com.portfolio.webhook.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A subscriber: an endpoint (and/or inbox) that wants to be notified about events.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "webhook_url", nullable = false)
    private String webhookUrl;

    /** HMAC-SHA256 signing key. Receivers use it to verify the payload. */
    @Column(nullable = false, length = 64)
    private String secret;

    @Column(name = "email_notify", nullable = false)
    private boolean emailNotify;

    @Column(name = "email_address")
    private String emailAddress;

    /** Event types this subscriber cares about, e.g. {@code ["order.paid"]}. Empty means "all". */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "event_types", nullable = false, columnDefinition = "text[]")
    private String[] eventTypes;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Returns true when this subscriber wants the given event type. */
    public boolean subscribesTo(String eventType) {
        if (eventTypes == null || eventTypes.length == 0) {
            return true; // no filter = subscribe to everything
        }
        for (String type : eventTypes) {
            if (type.equalsIgnoreCase(eventType)) {
                return true;
            }
        }
        return false;
    }
}
