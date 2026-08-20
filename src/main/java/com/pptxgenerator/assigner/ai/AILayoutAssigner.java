package com.pptxgenerator.assigner.ai;

import com.pptxgenerator.assigner.model.ClassifiedLayout;
import com.pptxgenerator.assigner.model.SlidePlanWithLayout;
import com.pptxgenerator.client.GenerativeAiGateway;
import com.pptxgenerator.client.builder.GenerativeAiRequestBuilder;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import com.pptxgenerator.planner.AiResponseParser;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class AILayoutAssigner {

    private final GenerativeAiGateway generativeAiGateway;
    private final AiResponseParser aiResponseParser;
    private final LayoutAssignmentPromptBuilder promptBuilder;

    /**
     * Réponse attendue de l'IA
     */
    public record AILayoutResponse(String layoutId, String rationale) {}

    /**
     * Appelle l'IA pour attribuer un layout à une slide content.
     */
    public Optional<AssignmentResult> assign(String purpose, String contentBrief,
                                              List<ClassifiedLayout> layoutsForAI,
                                              List<SlidePlanWithLayout> previousSlides) {
        if (layoutsForAI.isEmpty()) {
            return Optional.empty();
        }

        // Récupérer les IDs des 2 derniers layouts utilisés
        List<String> previousLayoutIds = previousSlides.stream()
            .skip(Math.max(0, previousSlides.size() - 2))
            .filter(s -> s.getLayout() != null)
            .map(s -> s.getLayout().getLayoutId())
            .toList();

        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(
            purpose, contentBrief, layoutsForAI, previousLayoutIds
        );

        try {
            TextRequestDto request = GenerativeAiRequestBuilder.builder()
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .build()
                .toRequest();
            TextResponseDto textResponse = generativeAiGateway.processRequest(request);
            String rawText = textResponse.getCandidates().get(0).getText();
            AILayoutResponse response = aiResponseParser.parseAs(rawText, AILayoutResponse.class);

            // Trouver le layout correspondant
            Optional<ClassifiedLayout> chosen = layoutsForAI.stream()
                .filter(l -> l.getLayoutId().equals(response.layoutId()))
                .findFirst();

            if (chosen.isPresent()) {
                return Optional.of(new AssignmentResult(chosen.get(), response.rationale()));
            } else {
                log.warn("IA a retourné un layout_id invalide: {}", response.layoutId());
                return Optional.of(new AssignmentResult(
                    layoutsForAI.get(0),
                    "Fallback (IA a retourné un ID invalide): " + response.rationale(),
                    "LAYOUT_FALLBACK"
                ));
            }

        } catch (Exception e) {
            log.error("Erreur appel IA: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public record AssignmentResult(ClassifiedLayout layout, String rationale, String warningCode) {
        public AssignmentResult(ClassifiedLayout layout, String rationale) {
            this(layout, rationale, null);
        }
    }
}
