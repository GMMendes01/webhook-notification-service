package br.com.portfolio.webhook.dto;

/** Result of a single delivery attempt, channel-agnostic. */
public record DeliveryOutcome(
        boolean success,
        Integer statusCode,
        String error,
        long elapsedMs
) {
}
