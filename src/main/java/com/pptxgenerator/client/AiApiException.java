package com.pptxgenerator.client;

/**
 * Thrown when an AI call failed with a permanent condition (HTTP 4xx other than
 * 429, malformed request): retrying cannot succeed.
 */
public class AiApiException extends RuntimeException {

    public AiApiException(String message) {
        super(message);
    }
}