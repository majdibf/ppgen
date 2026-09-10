package com.pptxgenerator.common.exception;

/**
 * The provided signature/token does not match the expected one.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}