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

        // Try to repair truncated JSON
        cleaned = repairTruncatedJson(cleaned);

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

        // If no complete JSON found, return from start to end
        return text.substring(start);
    }

    private String repairTruncatedJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        // Try to parse as-is first
        try {
            objectMapper.readTree(json);
            return json; // Valid JSON, no repair needed
        } catch (Exception e) {
            // JSON is invalid, try to repair
            log.debug("JSON is invalid, attempting repair: {}", e.getMessage());
        }

        String result = json.trim();
        
        // Remove incomplete objects from arrays
        // Look for patterns like: "..."},{"type":  (missing closing brace)
        result = removeIncompleteObjects(result);
        
        // Close any unclosed strings
        result = closeUnclosedStrings(result);
        
        // Remove trailing commas
        result = removeTrailingCommas(result);
        
        // Close unclosed brackets and braces
        result = closeUnclosedBrackets(result);

        log.debug("Repaired JSON: {}", result);
        return result;
    }
    
    private String removeIncompleteObjects(String json) {
        // Pattern: "},{"key":  (incomplete object after comma)
        // We need to find and remove incomplete objects
        StringBuilder result = new StringBuilder();
        int i = 0;
        boolean inString = false;
        boolean escaped = false;
        
        while (i < json.length()) {
            char c = json.charAt(i);
            
            if (escaped) {
                result.append(c);
                escaped = false;
                i++;
                continue;
            }
            
            if (c == '\\' && inString) {
                escaped = true;
                result.append(c);
                i++;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                result.append(c);
                i++;
                continue;
            }
            
            if (inString) {
                result.append(c);
                i++;
                continue;
            }
            
            // Check for pattern: "},{"  followed by incomplete object
            if (c == ',' && i + 1 < json.length() && json.charAt(i + 1) == '{') {
                // Look ahead to see if this object is complete
                int objStart = i + 1;
                int depth = 0;
                int j = objStart;
                boolean objComplete = false;
                
                while (j < json.length()) {
                    char ch = json.charAt(j);
                    if (ch == '{') depth++;
                    else if (ch == '}') {
                        depth--;
                        if (depth == 0) {
                            objComplete = true;
                            break;
                        }
                    }
                    j++;
                }
                
                if (!objComplete) {
                    // Object is incomplete, stop here
                    break;
                }
            }
            
            result.append(c);
            i++;
        }
        
        return result.toString();
    }
    
    private String closeUnclosedStrings(String json) {
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
            }
        }
        
        if (inString) {
            return json + "\"";
        }
        
        return json;
    }
    
    private String removeTrailingCommas(String json) {
        // Remove trailing commas before } or ]
        return json.replaceAll(",\\s*([}\\]])", "$1");
    }
    
    private String closeUnclosedBrackets(String json) {
        int openBraces = 0;
        int openBrackets = 0;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (inString) continue;
            
            if (c == '{') openBraces++;
            else if (c == '}') openBraces--;
            else if (c == '[') openBrackets++;
            else if (c == ']') openBrackets--;
        }
        
        StringBuilder closing = new StringBuilder(json);
        for (int i = 0; i < openBrackets; i++) {
            closing.append("]");
        }
        for (int i = 0; i < openBraces; i++) {
            closing.append("}");
        }
        
        return closing.toString();
    }
}
