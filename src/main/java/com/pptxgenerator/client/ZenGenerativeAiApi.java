package com.pptxgenerator.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pptxgenerator.client.dto.JsonSchemaDto;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Provider for OpenCode Zen (https://opencode.ai/zen), the model gateway of the
 * OpenCode team. Zen exposes different endpoint styles depending on the model family:
 *
 * <ul>
 *   <li>OpenAI-compatible {@code POST /chat/completions} for open models
 *       (GLM, DeepSeek, MiniMax, Kimi, free models...)</li>
 *   <li>Anthropic-compatible {@code POST /messages} for Claude and Qwen models</li>
 * </ul>
 *
 * <p>The endpoint style is resolved automatically from the model id (prefixes
 * {@code claude-}/ {@code qwen-} go to /messages), and can be forced through
 * configuration ({@code zen.endpoint-style}).
 */
@Slf4j
@ApplicationScoped
public class ZenGenerativeAiApi implements GenerativeAiApi {

    private static final String STYLE_CHAT = "chat";
    private static final String STYLE_MESSAGES = "messages";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ConfigProperty(name = "zen.api.url", defaultValue = "https://opencode.ai/zen/v1")
    public String apiUrl;

    @ConfigProperty(name = "zen.api.key")
    public Optional<String> apiKey;

    @ConfigProperty(name = "zen.model.default", defaultValue = "big-pickle")
    public String defaultModel;

    @ConfigProperty(name = "zen.request.timeout-seconds", defaultValue = "60")
    public long requestTimeoutSeconds;

    @ConfigProperty(name = "zen.endpoint-style", defaultValue = "auto")
    public String endpointStyle;

    @Override
    public TextResponseDto processGenerateAI(TextRequestDto request) {
        if (apiKey.isEmpty() || apiKey.get().isBlank()) {
            throw new IllegalStateException(
                    "ZEN_API_KEY is not set (configure zen.api.key)");
        }

        String model = request.getModelId() != null && !request.getModelId().isBlank()
                ? request.getModelId() : defaultModel;

        String style = resolveStyle(model);
        String response = STYLE_MESSAGES.equals(style)
                ? callMessages(model, request)
                : callChatCompletions(model, request);

        return new TextResponseDto(List.of(new TextResponseDto.TextCandidate(response)));
    }

    /**
     * Chooses the endpoint style for a model: Anthropic-compatible for Claude/Qwen,
     * OpenAI-compatible otherwise. Can be forced via {@code zen.endpoint-style}.
     */
    private String resolveStyle(String model) {
        if (STYLE_CHAT.equals(endpointStyle) || STYLE_MESSAGES.equals(endpointStyle)) {
            return endpointStyle;
        }
        String lower = model.toLowerCase();
        if (lower.startsWith("claude") || lower.startsWith("qwen")) {
            return STYLE_MESSAGES;
        }
        return STYLE_CHAT;
    }

    private String callChatCompletions(String model, TextRequestDto request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }

        ArrayNode messages = body.putArray("messages");
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.addObject().put("role", "system").put("content", request.getSystemPrompt());
        }
        messages.addObject().put("role", "user").put("content", request.getUserPrompt());

        appendJsonSchema(body, request);

        JsonNode root = send(model, "chat/completions", body, "Zen chat/completions call");
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new IllegalStateException("Zen response has no message content: " + root.asText());
        }
        return content.asText().trim();
    }

    private String callMessages(String model, TextRequestDto request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        } else {
            body.put("max_tokens", 4096);
        }
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            body.put("system", request.getSystemPrompt());
        }

        ArrayNode messages = body.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", request.getUserPrompt());

        JsonNode root = send(model, "messages", body, "Zen messages");
        JsonNode text = firstTextBlock(root.path("content"));
        if (text.isMissingNode() || text.isNull()) {
            throw new IllegalStateException("Zen messages response has no text block: " + root.asText());
        }
        return text.asText().trim();
    }

    private void appendJsonSchema(ObjectNode body, TextRequestDto request) {
        if (request.getOutputSchema() instanceof JsonSchemaDto schema) {
            ObjectNode responseFormat = body.putObject("response_format");
            responseFormat.put("type", "json_schema");
            ObjectNode jsonSchema = responseFormat.putObject("json_schema");
            jsonSchema.put("name", "response");
            jsonSchema.put("strict", false);
            jsonSchema.set("schema", objectMapper.valueToTree(schema));
        }
    }

    private JsonNode send(String model, String endpoint, ObjectNode body, String callName) {
        log.debug("[ZEN] Calling model={} via /{}", model, endpoint);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/" + endpoint))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.orElse(""))
                    .header("x-api-key", apiKey.orElse(""))
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> httpResponse =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() >= 300) {
                throw new IllegalStateException(
                        callName + " call failed: HTTP " + httpResponse.statusCode() + " - " + httpResponse.body());
            }
            return objectMapper.readTree(httpResponse.body());
        } catch (IOException e) {
            throw new RuntimeException(callName + " call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(callName + " call interrupted", e);
        }
    }

    /**
     * Anthropic-compatible responses return content as a list of typed blocks;
     * the first text block is the response payload.
     */
    private JsonNode firstTextBlock(JsonNode contentBlocks) {
        if (!contentBlocks.isArray()) {
            return contentBlocks;
        }
        for (JsonNode block : contentBlocks) {
            if ("text".equals(block.path("type").asText())) {
                return block.path("text");
            }
        }
        return null;
    }
}
