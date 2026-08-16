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
            if (attempt >= MAX_RETRIES) {
                log.warn("GenAI call failed after {} attempts.", MAX_RETRIES);
                throw e;
            }
            long delayMs = retryDelay(e, attempt);
            log.debug("Retry #{} in {}ms", attempt + 1, delayMs);
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Retry interrupted", ie);
            }
            return processRequestWithRetry(request, attempt + 1);
        }
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
