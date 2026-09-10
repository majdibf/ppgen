package com.pptxgenerator.pipeline.generator;

import com.pptxgenerator.common.TextTruncator;
import com.pptxgenerator.common.ai.ContentStagePrompt;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.ZoneKeys;
import com.pptxgenerator.pipeline.assigner.model.SlidePlanWithLayout;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Builds the AI prompts used to fill a content slide: a system prompt describing the per-zone-type
 * writing rules, and a user prompt enumerating the zones to fill. The prompt texts come from
 * {@link ContentStagePrompt}.
 */
@ApplicationScoped
public class ContentPromptBuilder {

    private static final String JSON_ONLY_DIRECTIVE =
            "IMPORTANT: You must respond with valid JSON only. Do not include any other text, markdown formatting, or explanations.";

    public String buildSystemPrompt() {
        return "Tu es un expert en rédaction de présentations professionnelles."
            + ContentStagePrompt.SYSTEM_BODY
            + "\n\n" + JSON_ONLY_DIRECTIVE;
    }

    public String buildUserPrompt(SlidePlanWithLayout slide,
                                  String previousSlideTitle,
                                  String nextSlidePurpose,
                                  String language,
                                  String tone,
                                  boolean webSearch,
                                  List<Zone> layoutZones) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("CONTEXTE DE LA PRÉSENTATION:\n");
        if (previousSlideTitle != null) {
            prompt.append("- Slide précédente: ").append(previousSlideTitle).append("\n");
        }
        if (nextSlidePurpose != null) {
            String truncated = TextTruncator.truncate(nextSlidePurpose, 100);
            prompt.append("- Slide suivante: ").append(truncated).append("\n");
        }

        prompt.append("\nINFORMATIONS SUR CETTE SLIDE:\n");
        prompt.append("- Numéro: ").append(slide.getSlideNumber()).append("\n");
        prompt.append("- Type: ").append(slide.getSlideType()).append("\n");
        prompt.append("- Purpose: ").append(slide.getPurpose()).append("\n");
        prompt.append("- Content brief: ").append(slide.getContentBrief()).append("\n");

        prompt.append("\nCONTEXTE DÉTAILLÉ (UTILISER TOUTES CES DONNÉES):\n");
        if (slide.getDetailedContext() != null && !slide.getDetailedContext().isBlank()) {
            prompt.append(slide.getDetailedContext()).append("\n");
        }

        prompt.append("\nZONES À REMPLIR:\n");
        for (Zone zone : layoutZones) {
            String zoneKey = ZoneKeys.key(zone);
            String desc = zone.getZoneDescription() != null ? zone.getZoneDescription() : "";
            prompt.append("- Zone_key: ").append(zoneKey).append("\n");
            prompt.append("  Type: ").append(zone.getZoneType().getValue()).append("\n");
            prompt.append("  Description: ").append(desc).append("\n");
            if (zone.getMaxCharacters() != null) {
                prompt.append("  Max characters: ").append(zone.getMaxCharacters()).append("\n");
            }
        }

        prompt.append("\nGénère maintenant le contenu exact pour chaque zone au format JSON.");
        return prompt.toString();
    }
}
