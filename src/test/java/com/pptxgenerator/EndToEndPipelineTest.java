package com.pptxgenerator;

import com.pptxgenerator.analyzer.TemplateAnalyzer;
import com.pptxgenerator.client.GenerativeAiGateway;
import com.pptxgenerator.client.GenerativeAiApi;
import com.pptxgenerator.client.OpenRouterGenerativeAiApi;
import com.pptxgenerator.client.GroqGenerativeAiApi;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import com.pptxgenerator.model.*;
import com.pptxgenerator.planner.AIContentGenerator;
import com.pptxgenerator.planner.AiResponseParser;
import com.pptxgenerator.planner.LayoutAssigner;
import com.pptxgenerator.planner.PresentationPlanner;
import com.pptxgenerator.renderer.PresentationRenderer;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EndToEndPipelineTest {

    private static final String TEMPLATE_PATH = "template_1.pptx";
    private static final String OUTPUT_PATH = "target/e2e-test-output.pptx";
    private static final String TOPIC = "Intelligence Artificielle et Machine Learning";
    private static final Map<String, String> envVars = new HashMap<>();

    static {
        loadEnvFile();
    }

    private static void loadEnvFile() {
        File envFile = new File(".env");
        if (!envFile.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int equalsIndex = line.indexOf('=');
                if (equalsIndex > 0) {
                    String key = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();
                    // Remove quotes if present
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    envVars.put(key, value);
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load .env file: " + e.getMessage());
        }
    }

    private static String getEnvVar(String key) {
        // First try system environment
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            return value;
        }
        // Then try .env file
        return envVars.get(key);
    }

    @Test
    void testFullPipelineWithRealAI() throws Exception {
        System.out.println("=== Starting End-to-End Pipeline Test with REAL AI ===\n");
        
        // Check if AI keys are configured (from env or .env file)
        String openRouterKey = getEnvVar("OPENROUTER_API_KEY");
        String groqKey = getEnvVar("GROQ_API_KEY");
        
        if ((openRouterKey == null || openRouterKey.isBlank()) && 
            (groqKey == null || groqKey.isBlank())) {
            System.out.println("⚠ No AI API keys found in environment variables.");
            System.out.println("  Set OPENROUTER_API_KEY or GROQ_API_KEY to use real AI.");
            System.out.println("  Running with mock AI instead...\n");
            testFullPipelineWithMockAI();
            return;
        }
        
        // Create real AI gateway
        GenerativeAiApi realAiApi;
        String provider;
        
        if (openRouterKey != null && !openRouterKey.isBlank()) {
            System.out.println("Using OpenRouter API");
            provider = "openrouter";
            realAiApi = createOpenRouterApi(openRouterKey);
        } else {
            System.out.println("Using Groq API");
            provider = "groq";
            realAiApi = createGroqApi(groqKey);
        }
        
        GenerativeAiGateway aiGateway = new GenerativeAiGateway();
        aiGateway.generativeAiApi = realAiApi;
        aiGateway.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        
        runPipeline(aiGateway, "REAL AI (" + provider + ")");
    }
    
    @Test
    void testFullPipelineWithMockAI() throws Exception {
        System.out.println("=== Starting End-to-End Pipeline Test with MOCK AI ===\n");
        GenerativeAiGateway mockGateway = createMockGateway();
        runPipeline(mockGateway, "MOCK AI");
    }
    
    private void runPipeline(GenerativeAiGateway aiGateway, String aiMode) throws Exception {
        // M1: Analyze template
        System.out.println("[M1] Analyzing template...");
        TemplateAnalyzer analyzer = new TemplateAnalyzer();
        TemplateStructure template = analyzer.analyze(TEMPLATE_PATH);
        
        assertNotNull(template);
        assertNotNull(template.getTheme());
        assertNotNull(template.getSlideDimensions());
        assertNotNull(template.getLayouts());
        assertTrue(template.getLayouts().size() > 0);
        
        System.out.println("  ✓ Template analyzed: " + template.getLayouts().size() + " layouts found");
        System.out.println("  ✓ Slide dimensions: " + template.getSlideDimensions().getWidth() + "x" + 
                          template.getSlideDimensions().getHeight() + " EMU");
        System.out.println();
        
        // M2: Generate plan
        System.out.println("[M2] Generating presentation plan with " + aiMode + "...");
        PresentationPlanner planner = new PresentationPlanner();
        planner.aiGateway = aiGateway;
        planner.responseParser = new AiResponseParser();
        
        PresentationPlan plan = planner.generatePlan(TOPIC, template);
        
        assertNotNull(plan);
        assertNotNull(plan.getSlides());
        assertTrue(plan.getSlides().size() > 0);
        
        System.out.println("  ✓ Plan generated: " + plan.getSlides().size() + " slides");
        System.out.println("  ✓ Title: " + plan.getTitle());
        System.out.println();
        
        // M3: Assign layouts
        System.out.println("[M3] Assigning layouts to slides...");
        LayoutAssigner assigner = new LayoutAssigner();
        EnrichedPlan enrichedPlan = assigner.assignLayouts(template, plan);
        
        assertNotNull(enrichedPlan);
        assertNotNull(enrichedPlan.getSlides());
        assertEquals(plan.getSlides().size(), enrichedPlan.getSlides().size());
        
        long assignedCount = enrichedPlan.getSlides().stream()
            .filter(s -> s.getAssignedLayout() != null)
            .count();
        long dynamicCount = enrichedPlan.getSlides().stream()
            .filter(EnrichedSlide::isDynamic)
            .count();
        
        System.out.println("  ✓ Layouts assigned: " + assignedCount + " slides with layouts");
        System.out.println("  ✓ Dynamic slides: " + dynamicCount);
        System.out.println();
        
        // M4: Generate content
        System.out.println("[M4] Generating content for slides with " + aiMode + "...");
        AIContentGenerator contentGenerator = new AIContentGenerator();
        contentGenerator.aiGateway = aiGateway;
        contentGenerator.responseParser = new AiResponseParser();
        
        ContentMap contentMap = contentGenerator.generateContent(enrichedPlan, TOPIC);
        
        assertNotNull(contentMap);
        assertEquals(enrichedPlan.getSlides().size(), contentMap.getTotalSlides());
        
        System.out.println("  ✓ Content generated for " + contentMap.getTotalSlides() + " slides");
        System.out.println();
        
        // M5: Render PPTX
        System.out.println("[M5] Rendering final PPTX...");
        PresentationRenderer renderer = new PresentationRenderer();
        
        File outputFile = renderer.render(template, enrichedPlan, contentMap, OUTPUT_PATH);
        
        assertNotNull(outputFile);
        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > 0);
        
        System.out.println("  ✓ PPTX rendered: " + outputFile.getAbsolutePath());
        System.out.println("  ✓ File size: " + (outputFile.length() / 1024) + " KB");
        System.out.println();
        
        System.out.println("=== End-to-End Pipeline Test PASSED ===");
        System.out.println("\nPipeline summary:");
        System.out.println("  M1: Template analyzed (" + template.getLayouts().size() + " layouts)");
        System.out.println("  M2: Plan generated (" + plan.getSlides().size() + " slides)");
        System.out.println("  M3: Layouts assigned (" + assignedCount + " matched, " + dynamicCount + " dynamic)");
        System.out.println("  M4: Content generated (" + contentMap.getTotalSlides() + " slides)");
        System.out.println("  M5: PPTX rendered (" + outputFile.getName() + ")");
        System.out.println("\nOutput file: " + outputFile.getAbsolutePath());
    }
    
    private GenerativeAiApi createOpenRouterApi(String apiKey) {
        OpenRouterGenerativeAiApi api = new OpenRouterGenerativeAiApi();
        api.apiUrl = "https://openrouter.ai/api/v1";
        api.apiKey = Optional.of(apiKey);
        api.defaultModel = "openai/gpt-3.5-turbo";
        return api;
    }
    
    private GenerativeAiApi createGroqApi(String apiKey) {
        GroqGenerativeAiApi api = new GroqGenerativeAiApi();
        api.apiUrl = "https://api.groq.com/openai/v1";
        api.apiKey = Optional.of(apiKey);
        api.defaultModel = "llama-3.3-70b-versatile";
        return api;
    }
    
    private GenerativeAiGateway createMockGateway() {
        return new GenerativeAiGateway() {
            @Override
            public TextResponseDto processRequest(TextRequestDto request) {
                String mockResponse;
                
                if (request.getUserPrompt() != null && request.getUserPrompt().toLowerCase().contains("plan")) {
                    mockResponse = """
                        {
                          "title": "Intelligence Artificielle et Machine Learning",
                          "slide_count": 6,
                          "slides": [
                            {
                              "type": "cover",
                              "title": "Intelligence Artificielle et Machine Learning",
                              "subtitle": "Une introduction complète"
                            },
                            {
                              "type": "content",
                              "title": "Qu'est-ce que l'IA?",
                              "bullet_points": [
                                "Définition de l'intelligence artificielle",
                                "Historique et évolution",
                                "Applications modernes"
                              ]
                            },
                            {
                              "type": "content",
                              "title": "Machine Learning",
                              "bullet_points": [
                                "Apprentissage supervisé",
                                "Apprentissage non supervisé",
                                "Apprentissage par renforcement"
                              ]
                            },
                            {
                              "type": "content",
                              "title": "Deep Learning",
                              "bullet_points": [
                                "Réseaux de neurones",
                                "Architectures modernes",
                                "Applications en vision et NLP"
                              ]
                            },
                            {
                              "type": "content",
                              "title": "Applications pratiques",
                              "bullet_points": [
                                "Santé et médecine",
                                "Finance et trading",
                                "Transport autonome"
                              ]
                            },
                            {
                              "type": "conclusion",
                              "title": "Conclusion",
                              "bullet_points": [
                                "L'IA transforme notre monde",
                                "Défis éthiques et sociaux",
                                "Perspectives futures"
                              ]
                            }
                          ]
                        }
                        """;
                } else {
                    mockResponse = "Contenu généré par l'IA pour la présentation sur l'intelligence artificielle.";
                }
                
                return new TextResponseDto(
                    java.util.List.of(new TextResponseDto.TextCandidate(mockResponse))
                );
            }
        };
    }
}
