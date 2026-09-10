package com.pptxgenerator.common.exception;

/**
 * The requested content does not exist.
 */
public class ContentNotFoundException extends RuntimeException {

    public ContentNotFoundException() {
        super("Content not found");
    }

    public ContentNotFoundException(String contentId) {
        super("Content not found: " + contentId);
    }
}