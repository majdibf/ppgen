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

@Slf4j
@ApplicationScoped
@Typed(OllamaGenerativeAiApi.class)
public class OllamaGenerativeAiApi implements GenerativeAiApi {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ConfigProperty(name = "ollama.api.url", defaultValue = "http://localhost:11434/v1")
    public String apiUrl;

    @ConfigProperty(name = "ollama.model.default", defaultValue = "llama3.2")
    public String defaultModel;

    @ConfigProperty(name = "ollama.request.timeout-seconds", defaultValue = "600")
    public long requestTimeoutSeconds;

    @Override
    public TextResponseDto processGenerativeAI(TextRequestDto request) {
        String model = request.getModelId() != null && !request.getModelId().isBlank()
                ? request.getModelId() : defaultModel;

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 2048);

        ArrayNode messages = body.putArray("messages");
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.addObject().put("role", "system").put("content", request.getSystemPrompt());
        }
        messages.addObject().put("role", "user").put("content", request.getUserPrompt());

        if (request.getOutputSchema() instanceof JsonSchemaDto) {
            body.putObject("response_format").put("type", "json_object");
        }

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            log.debug("[OLLAMA] Calling model={} at {}", model, apiUrl);
            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Ollama call failed: HTTP " + httpResponse.statusCode() + " - " + httpResponse.body());
            }

            String text = extractText(httpResponse.body());
            return new TextResponseDto(List.of(new TextResponseDto.TextCandidate(text)));

        } catch (IOException e) {
            throw new RuntimeException("Ollama call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Ollama call interrupted", e);
        }
    }

    private String extractText(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
            throw new IllegalStateException("Ollama response has no message content: " + responseBody);
        }
        return content.asText().trim();
    }
}