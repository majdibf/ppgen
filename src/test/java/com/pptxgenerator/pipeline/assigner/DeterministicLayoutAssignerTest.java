package com.pptxgenerator.pipeline.assigner;

import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.SemanticType;
import com.pptxgenerator.model.enums.ZoneType;
import com.pptxgenerator.pipeline.assigner.model.LayoutAssignmentResult;
import com.pptxgenerator.pipeline.planner.model.SlideType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DeterministicLayoutAssigner}.
 */
class DeterministicLayoutAssignerTest {

    private final DeterministicLayoutAssigner assigner = new DeterministicLayoutAssigner();

    @Test
    void assign_title_withTitleSlide_returnsTitleSlide_noWarning() {
        // Given
        LayoutAnalysis titleSlide = layout("L1", SemanticType.TITLE_SLIDE);
        LayoutAnalysis sectionHeader = layout("L2", SemanticType.SECTION_HEADER);
        List<LayoutAnalysis> layouts = List.of(sectionHeader, titleSlide);

        // When
        Optional<LayoutAssignmentResult> result = assigner.assign(SlideType.TITLE, layouts);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().layout()).isSameAs(titleSlide);
        assertThat(result.get().warningCode()).isNull();
    }

    @Test
    void assign_title_withoutTitleSlide_fallsBackToSectionHeader() {
        // Given
        LayoutAnalysis sectionHeader = layout("L2", SemanticType.SECTION_HEADER);
        List<LayoutAnalysis> layouts = List.of(sectionHeader);

        // When
        Optional<LayoutAssignmentResult> result = assigner.assign(SlideType.TITLE, layouts);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().layout()).isSameAs(sectionHeader);
        assertThat(result.get().warningCode()).isEqualTo("LAYOUT_FALLBACK");
    }

    @Test
    void assign_title_whenNoApplicableLayout_returnsEmpty() {
        // Given
        List<LayoutAnalysis> layouts = List.of(layout("L1", SemanticType.CONTENT));

        // When
        Optional<LayoutAssignmentResult> result = assigner.assign(SlideType.TITLE, layouts);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void assign_sectionTransition_withSectionHeader_returnsIt() {
        // Given
        LayoutAnalysis sectionHeader = layout("L2", SemanticType.SECTION_HEADER);
        LayoutAnalysis titleSlide = layout("L1", SemanticType.TITLE_SLIDE);
        List<LayoutAnalysis> layouts = List.of(titleSlide, sectionHeader);

        // When
        Optional<LayoutAssignmentResult> result = assigner.assign(SlideType.SECTION_TRANSITION, layouts);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().layout()).isSameAs(sectionHeader);
        assertThat(result.get().warningCode()).isNull();
    }

    @Test
    void assign_sectionTransition_withoutSectionHeader_fallsBackToTitleSlide() {
        // Given
        LayoutAnalysis titleSlide = layout("L1", SemanticType.TITLE_SLIDE);
        List<LayoutAnalysis> layouts = List.of(titleSlide);

        // When
        Optional<LayoutAssignmentResult> result = assigner.assign(SlideType.SECTION_TRANSITION, layouts);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().layout()).isSameAs(titleSlide);
        assertThat(result.get().warningCode()).isEqualTo("LAYOUT_FALLBACK");
    }

    @Test
    void assign_outline_withOutlineLayout_returnsIt() {
        // Given
        LayoutAnalysis outline = layout("L3", SemanticType.OUTLINE);
        List<LayoutAnalysis> layouts = List.of(layout("L1", SemanticType.CONTENT), outline);

        // When
        Optional<LayoutAssignmentResult> result = assigner.assign(SlideType.OUTLINE, layouts);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().layout()).isSameAs(outline);
        assertThat(result.get().warningCode()).isNull();
    }

    @Test
    void assign_outline_withoutOutline_picksContentWithLargestBodyZone() {
        // Given
        LayoutAnalysis smallContent = layoutWithBody("L1", 100L, 50L);
        LayoutAnalysis largeContent = layoutWithBody("L2", 300L, 300L);
        List<LayoutAnalysis> layouts = List.of(smallContent, largeContent);

        // When
        Optional<LayoutAssignmentResult> result = assigner.assign(SlideType.OUTLINE, layouts);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().layout()).isSameAs(largeContent);
        assertThat(result.get().warningCode()).isEqualTo("LAYOUT_FALLBACK");
    }

    @Test
    void assign_content_delegatesToAi_returnsEmpty() {
        // Given
        List<LayoutAnalysis> layouts = List.of(layout("L1", SemanticType.CONTENT));

        // When
        Optional<LayoutAssignmentResult> result = assigner.assign(SlideType.CONTENT, layouts);

        // Then
        assertThat(result).isEmpty();
    }

    private static LayoutAnalysis layout(String id, SemanticType type) {
        return LayoutAnalysis.builder()
            .layoutId(id)
            .semanticType(type)
            .build();
    }

    private static LayoutAnalysis layoutWithBody(String id, long width, long height) {
        return LayoutAnalysis.builder()
            .layoutId(id)
            .semanticType(SemanticType.CONTENT)
            .zones(List.of(Zone.builder()
                .zoneType(ZoneType.BODY)
                .width(width)
                .height(height)
                .build()))
            .build();
    }
}
