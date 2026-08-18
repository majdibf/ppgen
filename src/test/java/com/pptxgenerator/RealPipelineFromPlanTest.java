package com.pptxgenerator;

import com.pptxgenerator.analyzer.TemplateAnalyzer;
import com.pptxgenerator.client.GenerativeAiApi;
import com.pptxgenerator.client.GenerativeAiGateway;
import com.pptxgenerator.client.GroqGenerativeAiApi;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import com.pptxgenerator.model.ContentMap;
import com.pptxgenerator.model.EnrichedPlan;
import com.pptxgenerator.model.PresentationPlan;
import com.pptxgenerator.model.TemplateStructure;
import com.pptxgenerator.planner.AIContentGenerator;
import com.pptxgenerator.planner.AiResponseParser;
import com.pptxgenerator.planner.LayoutAssigner;
import com.pptxgenerator.planner.PresentationPlanner;
import com.pptxgenerator.renderer.PresentationRenderer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Runs M3-M5 against a captured plan and real Groq calls. */
class RealPipelineFromPlanTest {

    @Test
    void runRealLayoutContentAndRendererPipeline() throws Exception {
        String apiKey = envOrDotEnv("GROQ_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
            "GROQ_API_KEY absent: exporte-la ou ajoute-la dans .env");

        TemplateStructure template = new TemplateAnalyzer().analyze("template_1.pptx");
        PresentationPlan plan = plannerWithCapturedPlan().generatePlan("Tennis de table chinois", template);

        GenerativeAiGateway groqGateway = groqGateway(apiKey);
        LayoutAssigner assigner = new LayoutAssigner();
        assigner.aiGateway = groqGateway;
        assigner.responseParser = new AiResponseParser();
        EnrichedPlan enrichedPlan = assigner.assignLayouts(template, plan);

        AIContentGenerator contentGenerator = new AIContentGenerator();
        contentGenerator.aiGateway = groqGateway;
        contentGenerator.responseParser = new AiResponseParser();
        ContentMap content = contentGenerator.generateContent(enrichedPlan, "Tennis de table chinois");

        assertEquals(plan.getSlides().size(), content.getTotalSlides());
        assertTrue(enrichedPlan.getSlides().stream().allMatch(s -> s.getAssignedLayout() != null));

        String output = "target/real-pipeline-from-plan.pptx";
        new PresentationRenderer().render("template_1.pptx", template, enrichedPlan, content, output);
        assertTrue(Files.size(Path.of(output)) > 0);
    }

    private String envOrDotEnv(String key) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) return value;
        Path env = Path.of(".env");
        if (!Files.exists(env)) return null;
        try {
            for (String line : Files.readAllLines(env)) {
                String trimmed = line.trim();
                if (trimmed.startsWith(key + "=")) return trimmed.substring(key.length() + 1).trim();
            }
        } catch (Exception ignored) {
            // The assumption below reports the missing key to the test user.
        }
        return null;
    }

    private PresentationPlanner plannerWithCapturedPlan() {
        PresentationPlanner planner = new PresentationPlanner();
        planner.responseParser = new AiResponseParser();
        planner.aiGateway = new GenerativeAiGateway() {
            @Override
            public TextResponseDto processRequest(TextRequestDto request) {
                return new TextResponseDto(List.of(new TextResponseDto.TextCandidate(CAPTURED_PLAN)));
            }
        };
        return planner;
    }

    private GenerativeAiGateway groqGateway(String apiKey) {
        GroqGenerativeAiApi api = new GroqGenerativeAiApi();
        api.apiUrl = "https://api.groq.com/openai/v1";
        api.apiKey = Optional.of(apiKey);
        api.defaultModel = "openai/gpt-oss-20b";
        GenerativeAiGateway gateway = new GenerativeAiGateway();
        gateway.generativeAiApi = api;
        gateway.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return gateway;
    }

    private static final String CAPTURED_PLAN = """
        [
          {"type":"title","title":"Domination chinoise au tennis de table"},
          {"type":"outline","content":["Histoire","Techniques","Champions","Impact culturel"]},
          {"type":"section_transition","section_number":1,"title":"Contexte historique"},
          {"type":"content","title":"Dominance historique","body":["Domination depuis les années 1960","Plus de 60% des médailles olympiques","Sport national depuis 1959"]},
          {"type":"section_transition","section_number":2,"title":"Techniques et formation"},
          {"type":"content","title":"Formation intensive","body":["Shakehand et penhold","Formation dès 6-7 ans","Académies spécialisées"]},
          {"type":"content","title":"Champions légendaires","body":["Deng Yaping","Zhang Jike","Ma Long","Ding Ning"]},
          {"type":"content","title":"Impact culturel","body":["Fierté nationale","300 millions de pratiquants","Influence mondiale"]}
        ]
        """;
}
