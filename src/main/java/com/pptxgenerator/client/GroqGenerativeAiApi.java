package com.pptxgenerator.client;

import com.pptxgenerator.client.dto.JsonSchemaDto;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
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

@Slf4j
@ApplicationScoped
@Typed(GroqGenerativeAiApi.class)
public class GroqGenerativeAiApi implements GenerativeAiApi {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ConfigProperty(name = "groq.api.url", defaultValue = "https://api.groq.com/openai/v1")
    public String apiUrl;

    @ConfigProperty(name = "groq.api.key")
    public Optional<String> apiKey;

    @ConfigProperty(name = "groq.model.default", defaultValue = "openai/gpt-oss-20b")
    public String defaultModel;

    @Override
    public TextResponseDto processGenerativeAI(TextRequestDto request) {
        if (apiKey.isEmpty() || apiKey.get().isBlank()) {
            throw new IllegalStateException(
                    "GROQ_API_KEY is not set (app.ai.provider=groq requires a real API key)");
        }

        String model = request.getModelId() != null && !request.getModelId().isBlank()
                ? request.getModelId() : defaultModel;

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        // Increase max tokens to avoid truncated responses
        body.put("max_tokens", 4096);

        ArrayNode messages = body.putArray("messages");
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            String systemPrompt = request.getSystemPrompt();
            // Add JSON instruction to system prompt for Groq
            if (request.getOutputSchema() instanceof JsonSchemaDto) {
                systemPrompt += "\n\nIMPORTANT: You must respond with valid JSON only. Do not include any other text, markdown formatting, or explanations.";
            }
            messages.addObject().put("role", "system").put("content", systemPrompt);
        }
        messages.addObject().put("role", "user").put("content", request.getUserPrompt());

        // Note: Groq doesn't support response_format json_schema for all models
        // We rely on prompt instructions instead

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.orElse(""))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            log.debug("[GROQ] Calling model={}", model);
            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Groq call failed: HTTP " + httpResponse.statusCode() + " - " + httpResponse.body());
            }

            String text = extractText(httpResponse.body());
            return new TextResponseDto(List.of(new TextResponseDto.TextCandidate(text)));

        } catch (IOException e) {
            throw new RuntimeException("Groq call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Groq call interrupted", e);
        }
    }

    private String extractText(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new IllegalStateException("Groq response has no message content: " + responseBody);
        }
        return content.asText().trim();
    }
}
