package br.com.portfolio.webhook.dto;

import java.util.UUID;

/** 202 response returned by the ingest endpoint: the event is persisted, fan-out happens asynchronously. */
public record EventAcceptedResponse(
        UUID eventId,
        int deliveriesCreated,
        boolean duplicate
) {
}
