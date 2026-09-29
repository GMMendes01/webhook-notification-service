package br.com.portfolio.webhook.repository;

import br.com.portfolio.webhook.model.Delivery;
import br.com.portfolio.webhook.model.enums.DeliveryStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    List<Delivery> findByEventId(UUID eventId);

    /**
     * Loads a delivery together with its event and subscription in one round-trip.
     * The channel implementations read fields off both associations, and {@link #claim} clears the
     * persistence context, so the dispatcher must re-read the row after claiming it.
     */
    @Query("""
            SELECT d FROM Delivery d
            JOIN FETCH d.event
            JOIN FETCH d.subscription
            WHERE d.id = :id
            """)
    Optional<Delivery> findWithGraphById(@Param("id") UUID id);

    /**
     * Rows the dispatcher should consider: due in the past and not in a terminal state.
     * Ordered so the oldest work goes first.
     */
    @Query("""
            SELECT d FROM Delivery d
            WHERE d.status IN :statuses
              AND d.nextAttemptAt <= :now
            ORDER BY d.nextAttemptAt ASC
            """)
    List<Delivery> findDue(@Param("statuses") List<DeliveryStatus> statuses,
                           @Param("now") Instant now,
                           Pageable pageable);

    /**
     * Compare-and-swap claim: moves a delivery to DELIVERING only if it is still
     * exactly where the poller saw it. Returns 1 when this worker won the row,
     * 0 when another worker already took it — prevents double delivery.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Delivery d
            SET d.status = :delivering, d.updatedAt = :now
            WHERE d.id = :id
              AND d.status = :expectedStatus
              AND d.attemptCount = :expectedAttemptCount
            """)
    int claim(@Param("id") UUID id,
              @Param("expectedStatus") DeliveryStatus expectedStatus,
              @Param("expectedAttemptCount") int expectedAttemptCount,
              @Param("delivering") DeliveryStatus delivering,
              @Param("now") Instant now);

    Page<Delivery> findByStatus(DeliveryStatus status, Pageable pageable);

    Page<Delivery> findByEventId(UUID eventId, Pageable pageable);

    Page<Delivery> findBySubscriptionId(UUID subscriptionId, Pageable pageable);

    Page<Delivery> findByStatusAndEventId(DeliveryStatus status, UUID eventId, Pageable pageable);

    Page<Delivery> findByStatusAndSubscriptionId(DeliveryStatus status, UUID subscriptionId, Pageable pageable);
}
