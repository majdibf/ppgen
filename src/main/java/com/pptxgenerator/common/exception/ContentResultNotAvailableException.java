package com.pptxgenerator.common.exception;

/**
 * The content result is requested before the pipeline reached SUCCEEDED.
 */
public class ContentResultNotAvailableException extends RuntimeException {

    public ContentResultNotAvailableException(String contentId) {
        super("Content result not available yet, current status differs from SUCCEEDED: " + contentId);
    }
}