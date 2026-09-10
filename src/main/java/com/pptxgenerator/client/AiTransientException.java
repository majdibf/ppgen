package com.pptxgenerator.client;

/**
 * Thrown when an AI call failed with a transient condition (network error,
 * HTTP 429 or 5xx): a retry is meaningful.
 */
public class AiTransientException extends RuntimeException {

    public AiTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}