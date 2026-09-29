package br.com.portfolio.webhook.service;

import br.com.portfolio.webhook.dto.SubscriptionRequest;
import br.com.portfolio.webhook.exception.NotFoundException;
import br.com.portfolio.webhook.model.Subscription;
import br.com.portfolio.webhook.repository.SubscriptionRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SECRET_BYTES = 32;

    private final SubscriptionRepository repository;

    @Transactional
    public Subscription create(SubscriptionRequest request) {
        Subscription subscription = Subscription.builder()
                .name(request.name())
                .webhookUrl(request.webhookUrl())
                .secret(generateSecret())
                .eventTypes(request.eventTypes() == null
                        ? new String[0]
                        : request.eventTypes().toArray(String[]::new))
                .emailNotify(request.emailNotify())
                .emailAddress(request.emailAddress())
                .active(true)
                .createdAt(Instant.now())
                .build();
        return repository.save(subscription);
    }

    @Transactional(readOnly = true)
    public List<Subscription> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Subscription findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Subscription", id));
    }

    @Transactional
    public Subscription update(UUID id, SubscriptionRequest request) {
        Subscription subscription = findById(id);
        subscription.setName(request.name());
        subscription.setWebhookUrl(request.webhookUrl());
        subscription.setEventTypes(request.eventTypes() == null
                ? new String[0]
                : request.eventTypes().toArray(String[]::new));
        subscription.setEmailNotify(request.emailNotify());
        subscription.setEmailAddress(request.emailAddress());
        return repository.save(subscription);
    }

    @Transactional
    public void delete(UUID id) {
        Subscription subscription = findById(id);
        repository.delete(subscription);
    }

    @Transactional
    public String rotateSecret(UUID id) {
        Subscription subscription = findById(id);
        String secret = generateSecret();
        subscription.setSecret(secret);
        repository.save(subscription);
        return secret;
    }

    /** URL-safe random secret, 32 bytes of entropy. */
    private String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
