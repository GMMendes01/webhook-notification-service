-- Webhook Notification Service :: initial schema
-- Outbox-style: events and deliveries are persisted BEFORE any HTTP fan-out,
-- so a crash never loses an event.

-- gen_random_uuid() is built into PostgreSQL 13+ core, no extension needed.

CREATE TABLE subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    webhook_url     TEXT         NOT NULL,
    secret          VARCHAR(64)  NOT NULL,
    email_notify    BOOLEAN      NOT NULL DEFAULT FALSE,
    email_address   VARCHAR(255),
    event_types     TEXT[]       NOT NULL DEFAULT '{}',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE events (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type       VARCHAR(100) NOT NULL,
    payload          JSONB        NOT NULL,
    idempotency_key  VARCHAR(255) UNIQUE,
    received_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_type ON events (event_type);

CREATE TABLE deliveries (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id          UUID        NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    subscription_id   UUID        NOT NULL REFERENCES subscriptions (id) ON DELETE CASCADE,
    channel           VARCHAR(10) NOT NULL,
    target            TEXT        NOT NULL,
    status            VARCHAR(20) NOT NULL,
    attempt_count     INT         NOT NULL DEFAULT 0,
    max_attempts      INT         NOT NULL DEFAULT 5,
    next_attempt_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_status_code  INT,
    last_error        TEXT,
    response_ms       BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Drives the dispatcher poll: only due, not-yet-final rows.
CREATE INDEX idx_deliveries_dispatch ON deliveries (status, next_attempt_at);
CREATE INDEX idx_deliveries_event ON deliveries (event_id);
CREATE INDEX idx_deliveries_subscription ON deliveries (subscription_id);
