package com.pptxgenerator.analyzer;

import com.pptxgenerator.analyzer.prompt.LayoutClassificationPromptBuilder;
import com.pptxgenerator.client.GenerativeAiGateway;
import com.pptxgenerator.client.builder.GenerativeAiRequestBuilder;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.SlideDimensions;
import com.pptxgenerator.model.enums.SemanticType;
import com.pptxgenerator.planner.AiResponseParser;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class LayoutClassificationEnricher {

    private final GenerativeAiGateway generativeAiGateway;
    private final AiResponseParser aiResponseParser;
    private final LayoutClassificationPromptBuilder promptBuilder;

    public List<LayoutAnalysis> enrich(List<LayoutAnalysis> layouts, SlideDimensions dimensions) {
        log.info("Classification des layouts et enrichissement des descriptions via IA...");
        long startTime = System.currentTimeMillis();

        // Préparer les prompts (EXACTEMENT comme le Python)
        String systemPrompt = promptBuilder.buildSystemPrompt(dimensions);
        String userPrompt = promptBuilder.buildUserPrompt(layouts, dimensions);

        try {
            // Appel IA
            TextRequestDto request = GenerativeAiRequestBuilder.builder()
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .build()
                .toRequest();
            TextResponseDto response = generativeAiGateway.processRequest(request);
            String rawText = response.getCandidates().get(0).getText();
            Map<String, Object> enrichedData = aiResponseParser.parseAs(rawText, Map.class);

            // Parser la réponse
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> enrichedLayouts = (List<Map<String, Object>>) enrichedData.get("enriched_layouts");

            if (enrichedLayouts == null || enrichedLayouts.isEmpty()) {
                log.warn("Réponse IA vide ou mal formée, conservation des valeurs par défaut");
                return layouts;
            }

            // Extraire le mapping
            Map<String, Map<String, String>> enrichedLayoutsMap = new HashMap<>();
            for (Map<String, Object> item : enrichedLayouts) {
                String layoutId = (String) item.get("layout_id");
                String description = (String) item.get("layout_description");
                String semanticType = (String) item.get("semantic_type");

                if (layoutId != null && description != null && semanticType != null) {
                    enrichedLayoutsMap.put(layoutId, Map.of(
                        "description", description,
                        "semantic_type", semanticType
                    ));
                }
            }

            // Mettre à jour les descriptions et semantic_types
            for (LayoutAnalysis layout : layouts) {
                String layoutId = layout.getLayoutId();
                if (enrichedLayoutsMap.containsKey(layoutId)) {
                    Map<String, String> enriched = enrichedLayoutsMap.get(layoutId);
                    layout.setDescription(enriched.get("description"));
                    layout.setSemanticType(parseSemanticType(enriched.get("semantic_type")));
                    log.debug("{}: {} - description enrichie", layoutId, layout.getSemanticType());
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Classification terminée en {}ms", duration);
            return layouts;

        } catch (Exception e) {
            log.error("Erreur enrichissement layouts: {}", e.getMessage());
            log.warn("Descriptions de base conservées");
            return layouts;
        }
    }

    private SemanticType parseSemanticType(String value) {
        try {
            return SemanticType.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("semantic_type inconnu retourné par l'IA: '{}', repli sur CUSTOM", value);
            return SemanticType.CUSTOM;
        }
    }
}
