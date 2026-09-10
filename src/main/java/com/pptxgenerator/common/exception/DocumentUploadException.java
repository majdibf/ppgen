package com.pptxgenerator.common.exception;

/**
 * Generic storage failure for document upload/download.
 */
public class DocumentUploadException extends RuntimeException {

    public DocumentUploadException(String message) {
        super(message);
    }

    public DocumentUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}