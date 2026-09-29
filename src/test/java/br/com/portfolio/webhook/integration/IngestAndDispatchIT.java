package br.com.portfolio.webhook.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.portfolio.webhook.model.Delivery;
import br.com.portfolio.webhook.model.Subscription;
import br.com.portfolio.webhook.model.enums.DeliveryStatus;
import br.com.portfolio.webhook.repository.DeliveryRepository;
import br.com.portfolio.webhook.repository.SubscriptionRepository;
import br.com.portfolio.webhook.service.DispatchService;
import br.com.portfolio.webhook.service.EventIngestService;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IngestAndDispatchIT extends IntegrationTestBase {

    @Autowired EventIngestService ingestService;
    @Autowired DispatchService dispatchService;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired DeliveryRepository deliveryRepository;

    private MockWebServer receiver;

    @BeforeEach
    void startReceiver() throws Exception {
        receiver = new MockWebServer();
        receiver.start();
        deliveryRepository.deleteAll();
        subscriptionRepository.deleteAll();
    }

    @AfterEach
    void stopReceiver() throws Exception {
        receiver.shutdown();
    }

    private Subscription registerSubscriber(String... eventTypes) {
        return subscriptionRepository.save(Subscription.builder()
                .name("test-subscriber")
                .webhookUrl(receiver.url("/hook").toString())
                .secret("test-secret")
                .eventTypes(eventTypes)
                .emailNotify(false)
                .active(true)
                .createdAt(java.time.Instant.now())
                .build());
    }

    private void dispatchUntilSettled() {
        for (int i = 0; i < 10; i++) {
            dispatchService.dispatchDueDeliveries();
        }
    }

    @Test
    @DisplayName("ingest creates PENDING deliveries, dispatch delivers and signs them")
    void happyPath() throws InterruptedException {
        registerSubscriber("order.paid");
        receiver.enqueue(new MockResponse().setResponseCode(200));

        var accepted = ingestService.ingest("order.paid", Map.of("orderId", 42), "order-42");

        assertThat(accepted.duplicate()).isFalse();
        assertThat(accepted.deliveriesCreated()).isEqualTo(1);

        List<Delivery> pending = deliveryRepository.findByEventId(accepted.eventId());
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getStatus()).isEqualTo(DeliveryStatus.PENDING);

        dispatchUntilSettled();

        Delivery delivery = deliveryRepository.findByEventId(accepted.eventId()).get(0);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getResponseMs()).isNotNull();

        RecordedRequest request = awaitRequest();
        assertThat(request.getPath()).isEqualTo("/hook");
        assertThat(request.getHeader("X-Webhook-Signature")).startsWith("sha256=");
        assertThat(request.getHeader("X-Webhook-Event-Id")).isEqualTo(accepted.eventId().toString());
    }

    @Test
    @DisplayName("errors do not match a filtered subscriber's event types")
    void eventTypeFilteringIsRespected() {
        registerSubscriber("user.created");

        var accepted = ingestService.ingest("order.paid", Map.of("orderId", 1), null);

        assertThat(accepted.deliveriesCreated()).isZero();
        assertThat(deliveryRepository.findByEventId(accepted.eventId())).isEmpty();
    }

    @Test
    @DisplayName("exhausting retries moves the delivery to DEAD_LETTER, and replay revives it")
    void retriesThenDeadLetterThenReplay() {
        registerSubscriber("order.paid");
        // Fail more times than max-attempts (3 in the test profile).
        for (int i = 0; i < 5; i++) {
            receiver.enqueue(new MockResponse().setResponseCode(500));
        }

        var accepted = ingestService.ingest("order.paid", Map.of("orderId", 7), null);
        dispatchUntilSettled();

        Delivery delivery = deliveryRepository.findByEventId(accepted.eventId()).get(0);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DEAD_LETTER);
        assertThat(delivery.getAttemptCount()).isEqualTo(3);
        assertThat(delivery.getLastStatusCode()).isEqualTo(500);

        // Bring the receiver back, replay, and it should succeed.
        // Reset the FIFO queue first: we enqueued more 500s than the dispatcher consumed, and
        // MockWebServer would otherwise serve those leftovers ahead of the 200.
        receiver.setDispatcher(new QueueDispatcher());
        receiver.enqueue(new MockResponse().setResponseCode(200));
        dispatchService.replay(delivery.getId());
        dispatchUntilSettled();

        Delivery replayed = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(replayed.getStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(replayed.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the same idempotency key is not ingested twice")
    void idempotencyKeyIsHonoured() {
        registerSubscriber("order.paid");

        var first = ingestService.ingest("order.paid", Map.of("orderId", 1), "dup-key");
        var second = ingestService.ingest("order.paid", Map.of("orderId", 1), "dup-key");

        assertThat(second.duplicate()).isTrue();
        assertThat(second.eventId()).isEqualTo(first.eventId());
        assertThat(second.deliveriesCreated()).isZero();
    }

    private RecordedRequest awaitRequest() throws InterruptedException {
        RecordedRequest request = receiver.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(request).as("expected the receiver to be called within 5s").isNotNull();
        return request;
    }
}
