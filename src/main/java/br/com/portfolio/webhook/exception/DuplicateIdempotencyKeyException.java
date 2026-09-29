package br.com.portfolio.webhook.exception;

public class DuplicateIdempotencyKeyException extends RuntimeException {

    public DuplicateIdempotencyKeyException(String key) {
        super("An event with idempotency key '" + key + "' was already ingested");
    }
}
