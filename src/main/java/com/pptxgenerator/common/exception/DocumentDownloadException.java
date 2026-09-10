package com.pptxgenerator.common.exception;

/**
 * Generic storage failure for document download.
 */
public class DocumentDownloadException extends RuntimeException {

    public DocumentDownloadException(String message) {
        super(message);
    }

    public DocumentDownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}