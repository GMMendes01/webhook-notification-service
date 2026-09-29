package br.com.portfolio.webhook.controller;

import br.com.portfolio.webhook.dto.DeliveryResponse;
import br.com.portfolio.webhook.dto.EventAcceptedResponse;
import br.com.portfolio.webhook.dto.EventRequest;
import br.com.portfolio.webhook.dto.EventResponse;
import br.com.portfolio.webhook.model.Event;
import br.com.portfolio.webhook.repository.DeliveryRepository;
import br.com.portfolio.webhook.repository.EventRepository;
import br.com.portfolio.webhook.service.EventIngestService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventIngestService ingestService;
    private final EventRepository eventRepository;
    private final DeliveryRepository deliveryRepository;

    /** 202: accepted and persisted; fan-out happens asynchronously. */
    @PostMapping
    public ResponseEntity<EventAcceptedResponse> ingest(@Valid @RequestBody EventRequest request) {
        EventAcceptedResponse response = ingestService.ingest(
                request.eventType(), request.payload(), request.idempotencyKey());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public Page<EventResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return eventRepository.findAllByOrderByReceivedAtDesc(pageable)
                .map(e -> EventResponse.from(e, List.of()));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public EventResponse get(@PathVariable UUID id) {
        Event event = ingestService.findEvent(id);
        var deliveries = deliveryRepository.findByEventId(id).stream()
                .map(DeliveryResponse::from)
                .toList();
        return EventResponse.from(event, deliveries);
    }
}
