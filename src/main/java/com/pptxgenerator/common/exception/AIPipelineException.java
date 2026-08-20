package com.pptxgenerator.common.exception;

public class AIPipelineException extends RuntimeException {

    public AIPipelineException(String message) {
        super(message);
    }

    public AIPipelineException(String message, Throwable cause) {
        super(message, cause);
    }
}
