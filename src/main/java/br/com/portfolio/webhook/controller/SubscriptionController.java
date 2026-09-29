package br.com.portfolio.webhook.controller;

import br.com.portfolio.webhook.dto.SubscriptionRequest;
import br.com.portfolio.webhook.dto.SubscriptionResponse;
import br.com.portfolio.webhook.service.SubscriptionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService service;

    @PostMapping
    public ResponseEntity<SubscriptionResponse> create(@Valid @RequestBody SubscriptionRequest request) {
        SubscriptionResponse created = SubscriptionResponse.from(service.create(request));
        return ResponseEntity.created(URI.create("/api/subscriptions/" + created.id())).body(created);
    }

    @GetMapping
    public List<SubscriptionResponse> list() {
        return service.findAll().stream().map(SubscriptionResponse::from).toList();
    }

    @GetMapping("/{id}")
    public SubscriptionResponse get(@PathVariable UUID id) {
        return SubscriptionResponse.from(service.findById(id));
    }

    @PutMapping("/{id}")
    public SubscriptionResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody SubscriptionRequest request) {
        return SubscriptionResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/secret")
    public Map<String, String> rotateSecret(@PathVariable UUID id) {
        return Map.of("secret", service.rotateSecret(id));
    }
}
