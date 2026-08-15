package com.pptxgenerator.client;

import com.pptxgenerator.client.helper.AIMockProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class GenerativeAiApiProducer {

    @Inject
    OpenRouterGenerativeAiApi openRouterGenerativeAiApi;

    @Inject
    GroqGenerativeAiApi groqGenerativeAiApi;

    @ConfigProperty(name = "app.ai.mock", defaultValue = "true")
    boolean mockEnabled;

    @ConfigProperty(name = "app.ai.provider", defaultValue = "openrouter")
    String provider;

    @Produces
    @ApplicationScoped
    public GenerativeAiApi generativeAiApi() {
        if (mockEnabled) {
            return new AIMockProvider();
        }
        return switch (provider.toLowerCase()) {
            case "groq" -> groqGenerativeAiApi;
            case "openrouter" -> openRouterGenerativeAiApi;
            default -> throw new IllegalStateException(
                    "Unknown app.ai.provider: " + provider + " (expected 'openrouter' or 'groq')");
        };
    }
}
