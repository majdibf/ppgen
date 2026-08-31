package com.pptxgenerator.pipeline.planner;

import com.pptxgenerator.common.ai.PlanningStagePrompt;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the AI prompts used to generate a narrative plan. The prompt texts come from
 * {@link PlanningStagePrompt}.
 */
@ApplicationScoped
public class PlanningPromptBuilder {

    private static final String JSON_ONLY_DIRECTIVE =
            "IMPORTANT: You must respond with valid JSON only. Do not include any other text, markdown formatting, or explanations.";

    /**
     * System prompt for plan generation.
     */
    public String buildSystemPrompt() {
        return "Tu es un expert en création de présentations professionnelles."
            + PlanningStagePrompt.SYSTEM_BODY
            + "\n\n" + JSON_ONLY_DIRECTIVE;
    }

    /**
     * User prompt for plan generation.
     */
    public String buildUserPrompt(String instructions, List<String> inputs,
                                  int minSlides, int maxSlides,
                                  String language, String tone) {
        String contextBlock = (inputs == null || inputs.isEmpty())
            ? "Aucun contexte fourni."
            : inputs.stream()
                .map(input -> "- " + input)
                .collect(Collectors.joining("\n"));
        return PlanningStagePrompt.USER_TEMPLATE.formatted(instructions, contextBlock, minSlides, maxSlides);
    }
}
