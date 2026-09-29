package br.com.portfolio.webhook.service;

import br.com.portfolio.webhook.exception.NotFoundException;
import br.com.portfolio.webhook.model.Delivery;
import br.com.portfolio.webhook.model.enums.DeliveryStatus;
import br.com.portfolio.webhook.repository.DeliveryRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryQueryService {

    private final DeliveryRepository deliveryRepository;

    @Transactional(readOnly = true)
    public Delivery findById(UUID id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Delivery", id));
    }

    @Transactional(readOnly = true)
    public Page<Delivery> search(DeliveryStatus status, UUID eventId, UUID subscriptionId, Pageable pageable) {
        if (status != null && eventId != null) {
            return deliveryRepository.findByStatusAndEventId(status, eventId, pageable);
        }
        if (status != null && subscriptionId != null) {
            return deliveryRepository.findByStatusAndSubscriptionId(status, subscriptionId, pageable);
        }
        if (status != null) {
            return deliveryRepository.findByStatus(status, pageable);
        }
        if (eventId != null) {
            return deliveryRepository.findByEventId(eventId, pageable);
        }
        if (subscriptionId != null) {
            return deliveryRepository.findBySubscriptionId(subscriptionId, pageable);
        }
        return deliveryRepository.findAll(pageable);
    }
}
