package com.pptxgenerator.planner;

import com.pptxgenerator.client.GenerativeAiGateway;
import com.pptxgenerator.client.builder.GenerativeAiRequestBuilder;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import com.pptxgenerator.common.exception.AIPipelineException;
import com.pptxgenerator.planner.model.PlanResponse;
import com.pptxgenerator.planner.model.PresentationPlan;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PlanningService {

    private final GenerativeAiGateway generativeAiGateway;
    private final AiResponseParser aiResponseParser;
    private final PlanningPromptBuilder promptBuilder;
    private final PlanValidator validator;

    /**
     * Génère le plan narratif de la présentation (Step 1).
     *
     * @param instructions Instructions utilisateur
     * @param inputs       Contexte factuel (listes de textes)
     * @param minSlides    Nombre minimum de slides
     * @param maxSlides    Nombre maximum de slides
     * @param language     Langue (fr, en, etc.)
     * @param tone         Ton (professional, executive, creative, academic)
     * @return Le plan validé
     */
    public PresentationPlan generatePlan(String instructions, List<String> inputs,
                                         int minSlides, int maxSlides,
                                         String language, String tone) {

        log.info("Step 1: Génération du plan narratif");
        log.info("  Instructions: {}...", instructions.substring(0, Math.min(50, instructions.length())));
        log.info("  Slides: {} - {}", minSlides, maxSlides);
        log.info("  Langue: {}, Ton: {}", language, tone);

        // 1. Construire les prompts (IDENTIQUES au Python)
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(
            instructions, inputs, minSlides, maxSlides, language, tone
        );

        // 2. Appeler l'IA avec retry
        PlanResponse response = callWithRetry(systemPrompt, userPrompt);

        // 3. Extraire le plan
        PresentationPlan plan = response.getPresentationPlan();
        if (plan == null) {
            throw new AIPipelineException("L'IA n'a pas retourné de presentation_plan");
        }

        log.info("  Plan brut généré: {} slides", plan.getSlides() != null ? plan.getSlides().size() : 0);

        // 4. Valider et corriger (règles N1-N6)
        validator.validateAndFix(plan, minSlides, maxSlides);

        log.info("Step 1 terminé: '{}' ({} slides)", plan.getTitle(), plan.getTotalSlides());
        return plan;
    }

    /**
     * Appel LLM avec mécanisme de retry (3 tentatives, backoff exponentiel)
     */
    private PlanResponse callWithRetry(String systemPrompt, String userPrompt) {
        int maxRetries = 3;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("  Appel LLM (tentative {}/{})", attempt, maxRetries);
                TextRequestDto request = GenerativeAiRequestBuilder.builder()
                    .systemPrompt(systemPrompt)
                    .userPrompt(userPrompt)
                    .build()
                    .toRequest();
                TextResponseDto textResponse = generativeAiGateway.processRequest(request);
                String rawText = textResponse.getCandidates().get(0).getText();
                return aiResponseParser.parseAs(rawText, PlanResponse.class);

            } catch (Exception e) {
                lastException = e;
                log.warn("  Tentative {}/{} échouée: {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    sleep((long) Math.pow(2, attempt) * 1000);
                }
            }
        }

        throw new AIPipelineException(
            "Échec de la génération du plan après " + maxRetries + " tentatives", lastException
        );
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AIPipelineException("Interruption pendant le retry", e);
        }
    }
}
