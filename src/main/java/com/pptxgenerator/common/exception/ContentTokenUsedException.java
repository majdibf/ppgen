package com.pptxgenerator.common.exception;

/**
 * The single-use signature/token was already consumed for its action.
 */
public class ContentTokenUsedException extends RuntimeException {

    public ContentTokenUsedException(String message) {
        super(message);
    }
}