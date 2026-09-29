package br.com.portfolio.webhook.controller;

import br.com.portfolio.webhook.dto.DeliveryResponse;
import br.com.portfolio.webhook.model.enums.DeliveryStatus;
import br.com.portfolio.webhook.service.DeliveryQueryService;
import br.com.portfolio.webhook.service.DispatchService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryQueryService queryService;
    private final DispatchService dispatchService;

    @GetMapping
    public Page<DeliveryResponse> list(@RequestParam(required = false) DeliveryStatus status,
                                       @RequestParam(required = false) UUID eventId,
                                       @RequestParam(required = false) UUID subscriptionId,
                                       @PageableDefault(size = 20) Pageable pageable) {
        return queryService.search(status, eventId, subscriptionId, pageable)
                .map(DeliveryResponse::from);
    }

    @GetMapping("/{id}")
    public DeliveryResponse get(@PathVariable UUID id) {
        return DeliveryResponse.from(queryService.findById(id));
    }

    /** Re-queues a DEAD_LETTER delivery so the dispatcher picks it up on the next pass. */
    @PostMapping("/{id}/replay")
    public DeliveryResponse replay(@PathVariable UUID id) {
        return DeliveryResponse.from(dispatchService.replay(id));
    }
}
