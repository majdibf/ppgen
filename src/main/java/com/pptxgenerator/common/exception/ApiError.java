package com.pptxgenerator.common.exception;

/**
 * Standard error body for REST responses, produced by {@link ApiExceptionMapper}.
 *
 * <p>Serialized as: {@code { "error": { "code": "...", "message": "..." } }}.
 */
public record ApiError(String code, String message) {
}
