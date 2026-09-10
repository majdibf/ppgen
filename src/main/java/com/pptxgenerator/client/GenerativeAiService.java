package com.pptxgenerator.client;

import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import io.smallrye.common.annotation.CheckReturnValue;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.time.temporal.ChronoUnit;

/**
 * Aligned with the client project (fr.bpce.aiservices.contents.client.GenerativeAiService):
 * single entry point calling the generative-AI endpoint with automatic retry,
 * exponential backoff and jitter, delegated to MicroProfile Fault Tolerance.
 */
@Slf4j
@ApplicationScoped
public class GenerativeAiService {

    @Inject
    public GenerativeAiApi generativeAiApi;

    /**
     * Process a request with automatic retry, exponential backoff, and jitter.
     * Only TRANSIENT failures are retried (network errors, HTTP 429/5xx via
     * {@link AiTransientException}); permanent errors (bad key, HTTP 4xx,
     * malformed request) fail fast instead of burning retry budget.
     */
    @Retry(
        retryOn = AiTransientException.class,
        maxRetries = 2,
        delay = 1000,
        jitter = 2000,
        delayUnit = ChronoUnit.MILLIS
    )
    public TextResponseDto processRequestWithRetry(final TextRequestDto textRequest) {
        log.debug("[GenerativeAI] Calling Generative AI API");
        return generativeAiApi.processGenerateAI(textRequest);
    }
}
