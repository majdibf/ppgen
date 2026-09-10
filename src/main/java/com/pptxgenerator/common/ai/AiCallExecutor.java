package com.pptxgenerator.common.ai;

import com.pptxgenerator.client.GenerativeAiService;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Single entry point for every generative-AI call in the pipeline.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>build the {@link TextRequestDto} from a system prompt, user prompt, optional model id and
 *       optional output schema;</li>
 *   <li>delegate to {@link GenerativeAiService#processRequestWithRetry} which already applies
 *       retry + backoff + throttling (so stages must NOT retry on their own);</li>
 *   <li>parse the raw response via the shared {@link AiResponseParser}.</li>
 * </ul>
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class AiCallExecutor {

    private final GenerativeAiService generativeAiService;
    private final AiResponseParser parser;

    /**
     * Calls the AI and parses the response into the given type.
     *
     * @param modelId      provider model id, or {@code null} to use the service default
     * @param systemPrompt system instructions
     * @param userPrompt   user instructions
     * @param outputSchema optional JSON schema (provider-specific), or {@code null}
     * @param clazz        target type
     * @param <T>          target type
     * @return parsed response
     */
    public <T> T call(String modelId, String systemPrompt, String userPrompt, Object outputSchema, Class<T> clazz) {
        TextRequestDto request = TextRequestDto.builder()
                .modelId(modelId)
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .outputSchema(outputSchema)
                .build();
        TextResponseDto response = generativeAiService.processRequestWithRetry(request);
        String raw = extractSingleCandidate(response);
        return parser.parseAs(raw, clazz);
    }

    /**
     * Extracts the single candidate of the response with a guarded access: an
     * empty or null candidate list is a clear AI failure, not an IndexOutOfBounds.
     */
    private String extractSingleCandidate(TextResponseDto response) {
        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
            throw new IllegalStateException("AI response has no candidate: " + response);
        }
        return response.getCandidates().get(0).getText();
    }
}
