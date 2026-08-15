package com.pptxgenerator.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class AiResponseParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String extractJson(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalArgumentException("Empty AI response");
        }

        String cleaned = rawResponse.trim();

        cleaned = removeMarkdownFences(cleaned);

        cleaned = extractJsonBlock(cleaned);

        return cleaned.trim();
    }

    public <T> T parseAs(String rawResponse, Class<T> clazz) {
        try {
            String json = extractJson(rawResponse);
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("Failed to parse AI response as {}: {}", clazz.getSimpleName(), e.getMessage());
            log.debug("Raw response: {}", rawResponse);
            throw new RuntimeException("Failed to parse AI response", e);
        }
    }

    public JsonNode parseAsJsonNode(String rawResponse) {
        try {
            String json = extractJson(rawResponse);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("Failed to parse AI response as JSON: {}", e.getMessage());
            log.debug("Raw response: {}", rawResponse);
            throw new RuntimeException("Failed to parse AI response as JSON", e);
        }
    }

    private String removeMarkdownFences(String text) {
        Pattern fencePattern = Pattern.compile("```(?:json)?\\s*\\n?(.*?)\\n?\\s*```", Pattern.DOTALL);
        Matcher matcher = fencePattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return text;
    }

    private String extractJsonBlock(String text) {
        int braceStart = text.indexOf('{');
        int bracketStart = text.indexOf('[');

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
            return text;
        }

        int depth = 0;
        int end = -1;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);

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

        if (end >= 0) {
            return text.substring(start, end + 1);
        }

        return text.substring(start);
    }
}
