package com.pptxgenerator.analyzer;

import com.pptxgenerator.analyzer.prompt.ZoneDescriptionPromptBuilder;
import com.pptxgenerator.client.GenerativeAiGateway;
import com.pptxgenerator.client.builder.GenerativeAiRequestBuilder;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.SlideDimensions;
import com.pptxgenerator.model.Zone;
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
public class ZoneDescriptionEnricher {

    private final GenerativeAiGateway generativeAiGateway;
    private final AiResponseParser aiResponseParser;
    private final ZoneDescriptionPromptBuilder promptBuilder;

    public List<LayoutAnalysis> enrich(List<LayoutAnalysis> layouts, SlideDimensions dimensions) {
        log.info("Enrichissement des descriptions de zones via IA...");
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
            List<Map<String, Object>> enrichedZones = (List<Map<String, Object>>) enrichedData.get("enriched_zones");

            if (enrichedZones == null || enrichedZones.isEmpty()) {
                log.warn("Réponse IA vide ou mal formée, application des descriptions par défaut");
                return applyDefaultDescriptions(layouts);
            }

            // Créer un mapping (layout_id, zone_id) -> description
            Map<String, String> zoneDescriptions = new HashMap<>();
            for (Map<String, Object> item : enrichedZones) {
                String layoutId = (String) item.get("layout_id");
                Integer zoneId = (Integer) item.get("zone_id");
                String description = (String) item.get("zone_description");

                if (layoutId != null && zoneId != null && description != null) {
                    zoneDescriptions.put(layoutId + "_" + zoneId, description);
                }
            }

            // Mettre à jour les zones
            int zonesEnriched = 0;
            for (LayoutAnalysis layout : layouts) {
                for (Zone zone : layout.getZones()) {
                    String key = layout.getLayoutId() + "_" + zone.getZoneId();
                    if (zoneDescriptions.containsKey(key)) {
                        zone.setZoneDescription(zoneDescriptions.get(key));
                        zonesEnriched++;
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("{} zones enrichies en {}ms", zonesEnriched, duration);
            return layouts;

        } catch (Exception e) {
            log.error("Erreur enrichissement zones: {}", e.getMessage());
            return applyDefaultDescriptions(layouts);
        }
    }

    private List<LayoutAnalysis> applyDefaultDescriptions(List<LayoutAnalysis> layouts) {
        for (LayoutAnalysis layout : layouts) {
            for (Zone zone : layout.getZones()) {
                if (zone.getZoneDescription() == null) {
                    zone.setZoneDescription("Zone de type " + zone.getZoneType().getValue() +
                        " (" + zone.getSurfacePercentage() + "% de la surface)");
                }
            }
        }
        return layouts;
    }
}
