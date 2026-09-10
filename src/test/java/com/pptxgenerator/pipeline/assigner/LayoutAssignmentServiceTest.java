package com.pptxgenerator.pipeline.assigner;

import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.TemplateAnalysis;
import com.pptxgenerator.model.enums.SemanticType;
import com.pptxgenerator.pipeline.assigner.model.LayoutAssignmentResult;
import com.pptxgenerator.pipeline.assigner.model.LayoutAssignmentWarning;
import com.pptxgenerator.pipeline.assigner.model.PlanWithLayouts;
import com.pptxgenerator.pipeline.planner.model.PresentationPlan;
import com.pptxgenerator.pipeline.planner.model.SlidePlan;
import com.pptxgenerator.pipeline.planner.model.SlideType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LayoutAssignmentService} orchestration.
 */
@ExtendWith(MockitoExtension.class)
class LayoutAssignmentServiceTest {

    @Mock
    DeterministicLayoutAssigner deterministicAssigner;
    @Mock
    AILayoutAssigner aiAssigner;
    @Mock
    FallbackAssignment fallbackAssignment;
    @Mock
    LayoutAssignmentValidator validator;

    LayoutAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new LayoutAssignmentService(
            deterministicAssigner, aiAssigner, fallbackAssignment, validator);
        lenient().when(validator.validate(anyList())).thenReturn(List.of());
    }

    @Test
    void assignLayouts_noLayouts_throws() {
        // Given
        TemplateAnalysis analysis = TemplateAnalysis.builder().layouts(List.of()).build();
        PresentationPlan plan = plan(titleSlide(1));

        // When & Then
        assertThatThrownBy(() -> service.assignLayouts(plan, analysis, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no layouts");
    }

    @Test
    void assignLayouts_titleSlide_usesDeterministicAssigner() {
        // Given
        LayoutAnalysis titleLayout = layout("L1", SemanticType.TITLE_SLIDE);
        TemplateAnalysis analysis = TemplateAnalysis.builder().layouts(List.of(titleLayout)).build();
        when(deterministicAssigner.assign(SlideType.TITLE, List.of(titleLayout)))
            .thenReturn(Optional.of(new LayoutAssignmentResult(titleLayout, "auto", null)));
        PresentationPlan plan = plan(titleSlide(1));

        // When
        PlanWithLayouts result = service.assignLayouts(plan, analysis, null);

        // Then
        verify(aiAssigner, never()).assign(any(), any(), anyList(), anyList(), any());
        assertThat(result.getSlides()).hasSize(1);
        assertThat(result.getSlides().get(0).getLayout().getLayoutId()).isEqualTo("L1");
        assertThat(result.getSlides().get(0).getLayout().getSemanticType()).isEqualTo(SemanticType.TITLE_SLIDE);
    }

    @Test
    void assignLayouts_contentSlide_aiResultWins() {
        // Given
        LayoutAnalysis content = layout("L5", SemanticType.CONTENT);
        TemplateAnalysis analysis = TemplateAnalysis.builder().layouts(List.of(content)).build();
        when(deterministicAssigner.assign(SlideType.CONTENT, List.of(content))).thenReturn(Optional.empty());
        when(fallbackAssignment.filterUsableForContent(List.of(content))).thenReturn(List.of(content));
        when(aiAssigner.assign(any(), any(), eq(List.of(content)), anyList(), any()))
            .thenReturn(Optional.of(new LayoutAssignmentResult(content, "ai choice", null)));
        PresentationPlan plan = plan(contentSlide(1));

        // When
        PlanWithLayouts result = service.assignLayouts(plan, analysis, null);

        // Then
        assertThat(result.getSlides().get(0).getLayout().getLayoutId()).isEqualTo("L5");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void assignLayouts_contentSlide_aiEmpty_fallsBackWithWarning() {
        // Given
        LayoutAnalysis content = layout("L5", SemanticType.CONTENT);
        TemplateAnalysis analysis = TemplateAnalysis.builder().layouts(List.of(content)).build();
        when(deterministicAssigner.assign(SlideType.CONTENT, List.of(content))).thenReturn(Optional.empty());
        when(fallbackAssignment.filterUsableForContent(List.of(content))).thenReturn(List.of(content));
        when(aiAssigner.assign(any(), any(), eq(List.of(content)), anyList(), any())).thenReturn(Optional.empty());
        when(fallbackAssignment.findUltimateFallback(List.of(content), SlideType.CONTENT))
            .thenReturn(Optional.of(content));
        PresentationPlan plan = plan(contentSlide(1));

        // When
        PlanWithLayouts result = service.assignLayouts(plan, analysis, null);

        // Then
        assertThat(result.getSlides().get(0).getLayout().getLayoutId()).isEqualTo("L5");
        assertThat(result.getWarnings())
            .extracting(LayoutAssignmentWarning::getCode)
            .containsExactly("LAYOUT_FALLBACK");
    }

    @Test
    void assignLayouts_deterministicWarning_isCollected() {
        // Given
        LayoutAnalysis titleLayout = layout("L1", SemanticType.TITLE_SLIDE);
        TemplateAnalysis analysis = TemplateAnalysis.builder().layouts(List.of(titleLayout)).build();
        when(deterministicAssigner.assign(SlideType.TITLE, List.of(titleLayout)))
            .thenReturn(Optional.of(new LayoutAssignmentResult(titleLayout, "fallback", "LAYOUT_FALLBACK")));
        PresentationPlan plan = plan(titleSlide(1));

        // When
        PlanWithLayouts result = service.assignLayouts(plan, analysis, null);

        // Then
        assertThat(result.getSlides()).hasSize(1);
        assertThat(result.getSlides().get(0).getLayout().getLayoutId()).isEqualTo("L1");
        assertThat(result.getWarnings())
            .extracting(LayoutAssignmentWarning::getCode)
            .containsExactly("LAYOUT_FALLBACK");
        assertThat(result.getWarnings().get(0).getAffectedSlides()).containsExactly(1);
    }

    @Test
    void assignLayouts_nonContentSlideNotDetermined_usesFirstLayoutWithWarning() {
        // Given
        LayoutAnalysis outline = layout("L1", SemanticType.OUTLINE);
        TemplateAnalysis analysis = TemplateAnalysis.builder().layouts(List.of(outline)).build();
        when(deterministicAssigner.assign(SlideType.OUTLINE, List.of(outline))).thenReturn(Optional.empty());
        PresentationPlan plan = plan(outlineSlide(1));

        // When
        PlanWithLayouts result = service.assignLayouts(plan, analysis, null);

        // Then
        assertThat(result.getSlides().get(0).getLayout().getLayoutId()).isEqualTo("L1");
        assertThat(result.getWarnings())
            .extracting(LayoutAssignmentWarning::getCode)
            .containsExactly("LAYOUT_FALLBACK");
        verify(aiAssigner, never()).assign(any(), any(), anyList(), anyList(), any());
    }

    private static LayoutAnalysis layout(String id, SemanticType type) {
        return LayoutAnalysis.builder().layoutId(id).semanticType(type).build();
    }

    private static SlidePlan titleSlide(int number) {
        return SlidePlan.builder()
            .slideNumber(number)
            .slideType(SlideType.TITLE)
            .purpose("cover")
            .contentBrief("brief")
            .detailedContext("ctx")
            .build();
    }

    private static SlidePlan outlineSlide(int number) {
        return SlidePlan.builder()
            .slideNumber(number)
            .slideType(SlideType.OUTLINE)
            .purpose("summary")
            .contentBrief("brief")
            .detailedContext("ctx")
            .build();
    }

    private static SlidePlan contentSlide(int number) {
        return SlidePlan.builder()
            .slideNumber(number)
            .slideType(SlideType.CONTENT)
            .purpose("purpose")
            .contentBrief("brief")
            .detailedContext("ctx")
            .build();
    }

    private static PresentationPlan plan(SlidePlan... slides) {
        return PresentationPlan.builder()
            .title("T")
            .totalSlides(slides.length)
            .slides(List.of(slides))
            .build();
    }
}
