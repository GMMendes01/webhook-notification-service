package br.com.portfolio.webhook.service;

import br.com.portfolio.webhook.dto.DeliveryOutcome;
import br.com.portfolio.webhook.model.Delivery;
import br.com.portfolio.webhook.security.HmacSigner;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Performs the actual HTTP POST to a subscriber and signs the payload.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookDeliveryService {

    private final RestClient webhookRestClient;
    private final HmacSigner hmacSigner;

    public DeliveryOutcome deliver(Delivery delivery) {
        String body = delivery.getEvent().getPayload();
        long timestamp = Instant.now().getEpochSecond();
        String signature = hmacSigner.sign(body, delivery.getSubscription().getSecret(), timestamp);

        long start = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = webhookRestClient.post()
                    .uri(delivery.getTarget())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HmacSigner.SIGNATURE_HEADER, signature)
                    .header(HmacSigner.EVENT_ID_HEADER, delivery.getEvent().getId().toString())
                    .header(HmacSigner.TIMESTAMP_HEADER, String.valueOf(timestamp))
                    .body(body)
                    .retrieve()
                    // Do not let RestClient throw on 4xx/5xx: the status is part of our retry logic.
                    .onStatus(status -> true, (req, res) -> { })
                    .toEntity(String.class);

            long elapsed = System.currentTimeMillis() - start;
            int code = response.getStatusCode().value();
            boolean success = response.getStatusCode().is2xxSuccessful();
            String error = success ? null : "HTTP " + code;
            if (!success) {
                log.debug("Webhook to {} returned HTTP {}", delivery.getTarget(), code);
            }
            return new DeliveryOutcome(success, code, error, elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.debug("Webhook to {} failed: {}", delivery.getTarget(), e.getMessage());
            return new DeliveryOutcome(false, null, truncate(e.getMessage()), elapsed);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
