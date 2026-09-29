-- Demo subscriber pointing at a local fake receiver, so the ingest -> dispatch
-- flow can be exercised right after `docker compose up` + `mvn spring-boot:run`.

INSERT INTO subscriptions (name, webhook_url, secret, email_notify, email_address, event_types, active)
VALUES (
    'demo-local',
    'http://localhost:9090/hook',
    'demo-secret-please-rotate',
    TRUE,
    'demo@example.com',
    ARRAY['order.paid', 'user.created'],
    TRUE
);
