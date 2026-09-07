package com.pptxgenerator.client;

import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class GenerativeAiGateway {

    @Inject
    public GenerativeAiApi generativeAiApi;

    @Inject
    public ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 3;
    private static final long BACKOFF_BASE_MS = 500L;
    private final Object throttleLock = new Object();
    private volatile long lastRequestAt;

    @ConfigProperty(name = "app.ai.min-request-interval-ms", defaultValue = "1500")
    public long minRequestIntervalMs = 1500L;

    public TextResponseDto processRequest(TextRequestDto request) {
        return processRequestWithRetry(request, 1);
    }

    private TextResponseDto processRequestWithRetry(TextRequestDto request, int attempt) {
        log.debug("Calling GenAI - attempt {}/{}", attempt, MAX_RETRIES);

        try {
            waitForRateLimit();
            return generativeAiApi.processGenerativeAI(request);
        } catch (Exception e) {
            if (!isRetryable(e)) {
                log.error("GenAI call failed (non-retryable): {}: {}",
                        e.getClass().getSimpleName(), e.getMessage());
                throw e;
            }
            if (attempt >= MAX_RETRIES) {
                log.warn("GenAI call failed after {} attempts. Last error: {}: {}",
                        MAX_RETRIES, e.getClass().getSimpleName(), e.getMessage());
                throw e;
            }
            long delayMs = retryDelay(e, attempt);
            log.warn("GenAI call failed (attempt {}/{}), retryable: {}: {}. Retrying in {}ms",
                    attempt, MAX_RETRIES, e.getClass().getSimpleName(), e.getMessage(), delayMs);
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Retry interrupted", ie);
            }
            return processRequestWithRetry(request, attempt + 1);
        }
    }

    /**
     * Retrying is only useful for transient server-side conditions:
     * too-many-requests, server errors, request timeouts sent by the provider
     * (HTTP 408/429/5xx). Anything else fails fast — notably authentication
     * errors (4xx) and client-side timeouts, where a retry reproduces the
     * exact same latency and hides the root cause behind 3x the delay.
     */
    private boolean isRetryable(Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (e.getCause() != null && e.getCause().getMessage() != null) {
            message += " | " + e.getCause().getMessage();
        }
        String full = e.getClass().getSimpleName() + " " + message;

        // Client-side timeouts: retrying reproduces the same latency for nothing.
        if (full.contains("HttpTimeoutException") || full.contains("timed out")) {
            return false;
        }

        java.util.regex.Matcher statusMatcher =
                Pattern.compile("\\bHTTP (\\d{3})\\b").matcher(message);
        if (statusMatcher.find()) {
            int status = Integer.parseInt(statusMatcher.group(1));
            return status == 408 || status == 429 || status >= 500;
        }
        // Unknown errors (connection reset, parsing...) remain retryable as before.
        return true;
    }

    private void waitForRateLimit() {
        synchronized (throttleLock) {
            long wait = minRequestIntervalMs - (System.currentTimeMillis() - lastRequestAt);
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("AI request throttling interrupted", e);
                }
            }
            lastRequestAt = System.currentTimeMillis();
        }
    }

    private long retryDelay(Exception error, int attempt) {
        Matcher matcher = Pattern.compile("try again in ([0-9.]+)s").matcher(error.getMessage() == null ? "" : error.getMessage());
        if (matcher.find()) {
            return (long) (Double.parseDouble(matcher.group(1)) * 1000) + 250;
        }
        return BACKOFF_BASE_MS * (1L << (attempt - 1));
    }

    public List<TextResponseDto> processMultiRequests(List<TextRequestDto> requests) {
        return requests.stream()
                .map(this::processRequest)
                .toList();
    }

    public String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize object for AI request", e);
        }
    }
}
