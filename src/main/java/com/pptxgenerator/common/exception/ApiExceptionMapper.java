package com.pptxgenerator.common.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Maps pipeline/service exceptions to HTTP responses with a consistent
 * {@code { "error": { "code", "message" } }} body.
 */
@Slf4j
@Provider
public class ApiExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof NotFoundException) {
            return error(Response.Status.NOT_FOUND.getStatusCode(), "NOT_FOUND", exception.getMessage());
        }
        if (exception instanceof InvalidTokenException
                || exception instanceof ContentTokenUsedException) {
            return error(Response.Status.UNAUTHORIZED.getStatusCode(), "INVALID_TOKEN", exception.getMessage());
        }
        if (exception instanceof ContentResultNotAvailableException) {
            return error(Response.Status.NOT_FOUND.getStatusCode(), "NOT_READY", exception.getMessage());
        }
        if (exception instanceof DocumentUploadException) {
            return error(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                "DOCUMENT_UPLOAD_FAILED", exception.getMessage());
        }
        if (exception instanceof WebApplicationException wae) {
            return error(wae.getResponse().getStatus(), "BAD_REQUEST", exception.getMessage());
        }
        if (exception instanceof SecurityException) {
            return error(Response.Status.BAD_REQUEST.getStatusCode(), "INVALID_SIGNATURE", exception.getMessage());
        }
        if (exception instanceof IllegalArgumentException) {
            return error(Response.Status.BAD_REQUEST.getStatusCode(), "BAD_REQUEST", exception.getMessage());
        }
        if (exception instanceof IllegalStateException) {
            return error(Response.Status.CONFLICT.getStatusCode(), "NOT_READY", exception.getMessage());
        }
        log.error("Unhandled exception", exception);
        return error(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), "INTERNAL_ERROR", exception.getMessage());
    }

    private Response error(int status, String code, String message) {
        return Response.status(status)
                .entity(Map.of("error", new ApiError(code, message)))
                .build();
    }
}
