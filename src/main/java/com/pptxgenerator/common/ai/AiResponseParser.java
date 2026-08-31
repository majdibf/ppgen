package com.pptxgenerator.common.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleans up and deserializes generative-AI responses. Removes markdown fences and extracts
 * the JSON block before handing it to Jackson.
 */
@Slf4j
@ApplicationScoped
public class AiResponseParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern FENCE_PATTERN =
            Pattern.compile("```(?:json)?\\s*\\n?(.*?)\\n?\\s*```", Pattern.DOTALL);

    public <T> T parseAs(String rawResponse, Class<T> clazz) {
        try {
            return objectMapper.readValue(extractJson(rawResponse), clazz);
        } catch (Exception e) {
            log.error("Failed to parse AI response as {}: {}", clazz.getSimpleName(), e.getMessage());
            log.debug("Raw response: {}", rawResponse);
            throw new RuntimeException("Failed to parse AI response", e);
        }
    }

    private String extractJson(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalArgumentException("Empty AI response");
        }

        String cleaned = rawResponse.trim();

        // Remove markdown fences
        Matcher fenceMatcher = FENCE_PATTERN.matcher(cleaned);
        if (fenceMatcher.find()) {
            cleaned = fenceMatcher.group(1).trim();
        }

        // Extract first JSON block
        int braceStart = cleaned.indexOf('{');
        int bracketStart = cleaned.indexOf('[');
        int start;
        char openChar;
        char closeChar;

        if (braceStart >= 0 && (bracketStart < 0 || braceStart < bracketStart)) {
            start = braceStart;
            openChar = '{';
            closeChar = '}';
        } else if (bracketStart >= 0) {
            start = bracketStart;
            openChar = '[';
            closeChar = ']';
        } else {
            return cleaned;
        }

        int depth = 0;
        int end = -1;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;

            if (c == openChar) {
                depth++;
            } else if (c == closeChar) {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }

        return end >= 0 ? cleaned.substring(start, end + 1) : cleaned.substring(start);
    }
}
