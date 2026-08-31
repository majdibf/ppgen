package com.pptxgenerator.pipeline.assigner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptxgenerator.common.ai.LayoutAssignmentStagePrompt;
import com.pptxgenerator.model.LayoutAnalysis;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Builds the AI prompts used to assign a layout to a content slide: a system prompt describing the
 * visual-variety and adéquation rules, and a user prompt describing the slide and the candidate
 * layouts. The prompt texts come from {@link LayoutAssignmentStagePrompt}.
 */
@Slf4j
@ApplicationScoped
public class LayoutAssignmentPromptBuilder {

    private static final String JSON_ONLY_DIRECTIVE =
            "IMPORTANT: You must respond with valid JSON only. Do not include any other text, markdown formatting, or explanations.";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String buildSystemPrompt() {
        return "Tu es un expert en design de présentations PowerPoint."
            + LayoutAssignmentStagePrompt.SYSTEM_BODY
            + "\n\n" + JSON_ONLY_DIRECTIVE;
    }

    public String buildUserPrompt(String purpose, String contentBrief,
                                  List<LayoutAnalysis> layoutsForAI, List<String> previousLayoutIds) {
        try {
            String layoutsJson = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(layoutsForAI);
            String previousJson = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(previousLayoutIds);
            return LayoutAssignmentStagePrompt.USER_TEMPLATE.formatted(
                    purpose != null ? purpose : "Non spécifié",
                    contentBrief != null ? contentBrief : "Non spécifié",
                    layoutsJson,
                    previousJson
            );
        } catch (Exception e) {
            log.error("Erreur sérialisation JSON: {}", e.getMessage());
            return "Slide à traiter: " + purpose;
        }
    }
}
