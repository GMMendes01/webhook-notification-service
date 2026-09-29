package br.com.portfolio.webhook.service;

import br.com.portfolio.webhook.dto.DeliveryOutcome;
import br.com.portfolio.webhook.model.Delivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends the event notification by e-mail. Same delivery ledger as webhooks,
 * so retries/metrics work identically for both channels.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryService {

    private final JavaMailSender mailSender;

    public DeliveryOutcome deliver(Delivery delivery) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(delivery.getTarget());
        message.setFrom("no-reply@webhook-service.local");
        message.setSubject("Event: " + delivery.getEvent().getEventType());
        message.setText("""
                Event type: %s
                Event id:   %s
                Attempt:    %d

                Payload:
                %s
                """.formatted(
                delivery.getEvent().getEventType(),
                delivery.getEvent().getId(),
                delivery.getAttemptCount() + 1,
                delivery.getEvent().getPayload()));

        long start = System.currentTimeMillis();
        try {
            mailSender.send(message);
            return new DeliveryOutcome(true, null, null, System.currentTimeMillis() - start);
        } catch (MailException e) {
            log.debug("E-mail to {} failed: {}", delivery.getTarget(), e.getMessage());
            return new DeliveryOutcome(false, null, e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
