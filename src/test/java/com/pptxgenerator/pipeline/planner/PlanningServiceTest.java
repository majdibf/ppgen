package com.pptxgenerator.pipeline.planner;

import com.pptxgenerator.common.ai.AiCallExecutor;
import com.pptxgenerator.common.exception.AIPipelineException;
import com.pptxgenerator.pipeline.planner.model.PlanResponse;
import com.pptxgenerator.pipeline.planner.model.PresentationPlan;
import com.pptxgenerator.pipeline.planner.model.SlidePlan;
import com.pptxgenerator.pipeline.planner.model.SlideType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PlanningService}.
 *
 * <p>All collaborators are mocked so the tests focus on the orchestration done by
 * {@link PlanningService} only (prompt building, AI call, validation, error handling).
 */
@ExtendWith(MockitoExtension.class)
class PlanningServiceTest {

    @Mock
    AiCallExecutor aiCallExecutor;
    @Mock
    PlanningPromptBuilder promptBuilder;
    @Mock
    PlanValidator validator;

    PlanningService service;

    @BeforeEach
    void setUp() {
        service = new PlanningService(aiCallExecutor, promptBuilder, validator);
    }

    @Test
    void generatePlan_buildsAndCallsAi_thenValidatesAndReturnsPlan() {
        // Given
        PresentationPlan aiPlan = plan(
            slide(1, SlideType.TITLE),
            slide(2, SlideType.CONTENT));
        when(promptBuilder.buildSystemPrompt()).thenReturn("system");
        when(promptBuilder.buildUserPrompt("instructions", List.of("c1"), 2, 10, "fr", "PROFESSIONAL"))
            .thenReturn("user");
        when(aiCallExecutor.call(any(), eq("system"), eq("user"), any(), eq(PlanResponse.class)))
            .thenReturn(PlanResponse.builder().presentationPlan(aiPlan).build());

        // When
        PresentationPlan result = service.generatePlan(
            "instructions", List.of("c1"), 2, 10, "fr", "PROFESSIONAL", null);

        // Then
        assertThat(result).isSameAs(aiPlan);
        verify(validator).validateAndFix(aiPlan, 2, 10);
    }

    @Test
    void generatePlan_missingPresentationPlan_throws() {
        // Given
        when(promptBuilder.buildSystemPrompt()).thenReturn("system");
        when(promptBuilder.buildUserPrompt(any(), any(), any(Integer.class), any(Integer.class), any(), any()))
            .thenReturn("user");
        when(aiCallExecutor.call(any(), any(), any(), any(), eq(PlanResponse.class)))
            .thenReturn(PlanResponse.builder().presentationPlan(null).build());

        // When & Then
        assertThatThrownBy(() ->
            service.generatePlan("instructions", List.of(), 2, 10, "fr", "PROFESSIONAL", null))
            .isInstanceOf(AIPipelineException.class);
    }

    private static PresentationPlan plan(SlidePlan... slides) {
        return PresentationPlan.builder()
            .title("Intro")
            .totalSlides(slides.length)
            .slides(List.of(slides))
            .build();
    }

    private static SlidePlan slide(int number, SlideType type) {
        return SlidePlan.builder()
            .slideNumber(number)
            .slideType(type)
            .purpose("purpose " + number)
            .contentBrief("brief " + number)
            .detailedContext("context " + number)
            .build();
    }
}
