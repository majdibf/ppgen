package com.pptxgenerator.pipeline.assigner;

import com.pptxgenerator.common.ai.AiCallExecutor;
import com.pptxgenerator.common.ai.OutputSchemaProvider;
import com.pptxgenerator.pipeline.assigner.model.LayoutAssignmentResult;
import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.pipeline.assigner.model.SlidePlanWithLayout;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * Assigns a layout to a content slide using the generative AI gateway, honouring the same
 * visual-variety and adéquation rules as the deterministic assigner but with an LLM choice.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class AILayoutAssigner {

    private final AiCallExecutor aiCallExecutor;
    private final LayoutAssignmentPromptBuilder promptBuilder;

    public Optional<LayoutAssignmentResult> assign(String purpose,
                                                   String modelId,
                                                   String contentBrief,
                                                   List<LayoutAnalysis> usableForContent,
                                                   List<SlidePlanWithLayout> previousSlides) {
        try {
            List<String> previousLayoutIds = previousSlides.stream()
                    .map(s -> s.getLayout() != null ? s.getLayout().getLayoutId() : "")
                    .toList();

            String systemPrompt = promptBuilder.buildSystemPrompt();
            String userPrompt = promptBuilder.buildUserPrompt(purpose, contentBrief, usableForContent, previousLayoutIds);

            AiLayoutResponse response = aiCallExecutor.call(modelId, systemPrompt, userPrompt,
                    OutputSchemaProvider.createLayoutSchema(), AiLayoutResponse.class);

            String layoutId = response.getLayoutId();
            String rationale = response.getRationale() != null ? response.getRationale() : "";

            if (layoutId == null || layoutId.isBlank()) {
                log.warn("AI returned null or blank layout_id");
                return Optional.empty();
            }
            return usableForContent.stream()
                    .filter(l -> l.getLayoutId().equals(layoutId))
                    .findFirst()
                    .map(layout -> new LayoutAssignmentResult(layout, rationale, null));
        } catch (Exception e) {
            log.error("AI layout assignment failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
