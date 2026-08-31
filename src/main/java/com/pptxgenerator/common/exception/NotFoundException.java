package com.pptxgenerator.common.exception;

/**
 * Signals that a requested resource does not exist. Mapped to HTTP 404 by
 * {@link ApiExceptionMapper}.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
