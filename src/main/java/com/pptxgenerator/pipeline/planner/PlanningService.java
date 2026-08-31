package com.pptxgenerator.pipeline.planner;

import com.pptxgenerator.common.ai.AiCallExecutor;
import com.pptxgenerator.common.exception.AIPipelineException;
import com.pptxgenerator.pipeline.planner.model.PlanResponse;
import com.pptxgenerator.pipeline.planner.model.PresentationPlan;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PlanningService {

    private final AiCallExecutor aiCallExecutor;
    private final PlanningPromptBuilder promptBuilder;
    private final PlanValidator validator;

    /**
     * Generates the narrative plan of the presentation (Step 1).
     *
     * @param instructions User instructions
     * @param inputs       Factual context (lists of texts)
     * @param minSlides    Minimum number of slides
     * @param maxSlides    Maximum number of slides
     * @param language     Language (fr, en, etc.)
     * @param tone         Tone (professional, executive, creative, academic)
     * @return the validated plan
     */
    public PresentationPlan generatePlan(String instructions, List<String> inputs,
                                         int minSlides, int maxSlides,
                                         String language, String tone) {

        log.info("Step 1: Génération du plan narratif (slides: {}-{}, langue: {}, ton: {})",
                minSlides, maxSlides, language, tone);

        // 1. Build the prompts
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(
            instructions, inputs, minSlides, maxSlides, language, tone
        );

        // 2. Call the AI (retry/backoff/throttling centralized in AiCallExecutor + GenerativeAiGateway)
        String outputSchema = promptBuilder.buildOutputSchema();
        PlanResponse response = aiCallExecutor.call(null, systemPrompt, userPrompt, outputSchema, PlanResponse.class);

        // 3. Extract the plan
        PresentationPlan plan = response.getPresentationPlan();
        if (plan == null) {
            throw new AIPipelineException("L'IA n'a pas retourné de presentation_plan");
        }

        log.info("  Plan brut généré: {} slides", plan.getSlides() != null ? plan.getSlides().size() : 0);

        // 4. Validate and fix (rules N1-N6)
        validator.validateAndFix(plan, minSlides, maxSlides);

        log.info("Step 1 terminé: '{}' ({} slides)", plan.getTitle(), plan.getTotalSlides());
        return plan;
    }
}
