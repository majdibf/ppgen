package com.pptxgenerator.pipeline.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptxgenerator.common.ai.TemplateAnalysisStagePrompt;
import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.SlideDimensions;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ZoneType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the AI prompts used by {@link TemplateAnalyzer}: zone-enrichment and
 * layout-classification system and user prompts. The prompt texts come from
 * {@link TemplateAnalysisStagePrompt}.
 */
@Slf4j
@ApplicationScoped
public class AnalyzerPromptBuilder {

    private static final String JSON_ONLY_DIRECTIVE =
            "IMPORTANT: You must respond with valid JSON only. Do not include any other text, markdown formatting, or explanations.";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String buildZoneSystemPrompt(long width, long height) {
        return systemPrompt(TemplateAnalysisStagePrompt.ZONE_SYSTEM_BODY, width, height);
    }

    public String buildLayoutSystemPrompt(long width, long height) {
        return systemPrompt(TemplateAnalysisStagePrompt.LAYOUT_SYSTEM_BODY, width, height);
    }

    private String systemPrompt(String systemBody, long width, long height) {
        return "Tu es un expert en analyse de layouts PowerPoint."
            + systemBody.formatted(width, height)
            + "\n\n" + JSON_ONLY_DIRECTIVE;
    }

    public String buildZoneUserPrompt(List<LayoutAnalysis> layouts, SlideDimensions dimensions) {
        try {
            List<Map<String, Object>> context = new ArrayList<>();
            for (LayoutAnalysis layout : layouts) {
                List<Map<String, Object>> zones = new ArrayList<>();
                for (Zone zone : layout.getZones()) {
                    Map<String, Object> z = new LinkedHashMap<>();
                    z.put("zone_id", zone.getZoneId());
                    z.put("zone_type", zone.getZoneType().getValue());
                    z.put("surface_percentage", zone.getSurfacePercentage());
                    z.put("position", zone.getPosition());
                    z.put("width", zone.getWidth());
                    z.put("height", zone.getHeight());
                    z.put("z_index", zone.getZIndex());
                    z.put("top_left_x", zone.getPolygon().get(0).getX());
                    z.put("top_left_y", zone.getPolygon().get(0).getY());
                    if (zone.getZoneType() == ZoneType.WORD || zone.getZoneType() == ZoneType.LINE) {
                        double widthPercentage = (zone.getWidth() / (double) dimensions.getWidth()) * 100;
                        z.put("width_percentage", Math.round(widthPercentage * 10.0) / 10.0);
                    }
                    zones.add(z);
                }
                Map<String, Object> layoutCtx = new LinkedHashMap<>();
                layoutCtx.put("layout_id", layout.getLayoutId());
                layoutCtx.put("semantic_type", layout.getSemanticType().name());
                layoutCtx.put("layout_description", layout.getDescription());
                layoutCtx.put("zones", zones);
                context.add(layoutCtx);
            }
            String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(context);
            return "Layouts avec zones à analyser:\n" + json
                + "\n\nGénère une description enrichie pour chaque zone de chaque layout.";
        } catch (Exception e) {
            log.error("Erreur sérialisation JSON: {}", e.getMessage());
            return "Layouts avec zones à analyser: []";
        }
    }

    public String buildLayoutUserPrompt(List<LayoutAnalysis> layouts) {
        try {
            List<Map<String, Object>> context = new ArrayList<>();
            for (LayoutAnalysis layout : layouts) {
                List<Map<String, Object>> zonesWithDescriptions = new ArrayList<>();
                for (Zone zone : layout.getZones()) {
                    Map<String, Object> zoneInfo = new LinkedHashMap<>();
                    zoneInfo.put("zone_id", zone.getZoneId());
                    zoneInfo.put("zone_type", zone.getZoneType().getValue());
                    zoneInfo.put("surface_percentage", zone.getSurfacePercentage());
                    zoneInfo.put("position", zone.getPosition());
                    zoneInfo.put("zone_description", zone.getZoneDescription() != null
                        ? zone.getZoneDescription() : "Zone de type " + zone.getZoneType().getValue());
                    zonesWithDescriptions.add(zoneInfo);
                }
                Map<String, Object> layoutContext = new LinkedHashMap<>();
                layoutContext.put("layout_id", layout.getLayoutId());
                layoutContext.put("original_name", layout.getOriginalName());
                layoutContext.put("zones", zonesWithDescriptions);
                context.add(layoutContext);
            }
            String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(context);
            return "Layouts à analyser:\n" + json
                + "\n\nGénère la description ET ensuite classifie le semantic_type pour chaque layout.";
        } catch (Exception e) {
            log.error("Erreur sérialisation JSON: {}", e.getMessage());
            return "Layouts à analyser: []";
        }
    }
}
