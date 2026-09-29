package br.com.portfolio.webhook.exception;

/** Thrown when an operation is not legal for the current resource state (e.g. replay of a non-dead-letter delivery). */
public class InvalidStateException extends RuntimeException {

    public InvalidStateException(String message) {
        super(message);
    }
}
