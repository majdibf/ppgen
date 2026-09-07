package com.pptxgenerator.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class GenerativeAiApiProducer {

    @Inject
    OllamaGenerativeAiApi ollamaGenerativeAiApi;

    @Inject
    OpenRouterGenerativeAiApi openRouterGenerativeAiApi;

    @Inject
    ZenGenerativeAiApi zenGenerativeAiApi;

    @ConfigProperty(name = "app.ai.provider", defaultValue = "ollama")
    String provider;

    @Produces
    @ApplicationScoped
    public GenerativeAiApi generativeAiApi() {
        return switch (provider.toLowerCase()) {
            case "ollama" -> ollamaGenerativeAiApi;
            case "openrouter" -> openRouterGenerativeAiApi;
            case "zen" -> zenGenerativeAiApi;
            default -> throw new IllegalStateException(
                    "Unknown app.ai.provider: " + provider + " (expected 'ollama', 'openrouter' or 'zen')");
        };
    }
}