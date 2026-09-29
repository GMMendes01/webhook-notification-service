package br.com.portfolio.webhook.model.enums;

/**
 * Lifecycle of a single delivery attempt chain.
 *
 * <p>{@code PENDING}  - waiting to be picked up by the dispatcher.
 * <p>{@code DELIVERING} - claimed by a worker (in flight).
 * <p>{@code SUCCESS} - terminal, 2xx (or SMTP accepted).
 * <p>{@code FAILED}  - retryable failure, a future attempt is scheduled.
 * <p>{@code DEAD_LETTER} - terminal, attempts exhausted. Can be replayed via the API.
 */
public enum DeliveryStatus {
    PENDING,
    DELIVERING,
    SUCCESS,
    FAILED,
    DEAD_LETTER;

    /** True when the row has reached a state the dispatcher will never touch again. */
    public boolean isTerminal() {
        return this == SUCCESS || this == DEAD_LETTER;
    }
}
