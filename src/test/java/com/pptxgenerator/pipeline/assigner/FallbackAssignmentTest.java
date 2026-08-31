package com.pptxgenerator.pipeline.assigner;

import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.SemanticType;
import com.pptxgenerator.model.enums.ZoneType;
import com.pptxgenerator.pipeline.planner.model.SlideType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FallbackAssignment}.
 */
class FallbackAssignmentTest {

    private final FallbackAssignment fallback = new FallbackAssignment();

    @Test
    void findUltimateFallback_emptyLayouts_returnsEmpty() {
        // When
        Optional<LayoutAnalysis> result = fallback.findUltimateFallback(List.of(), SlideType.CONTENT);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void findUltimateFallback_title_prefersTitleSlide() {
        // Given
        LayoutAnalysis content = layout("L0", SemanticType.CONTENT);
        LayoutAnalysis titleSlide = layout("L1", SemanticType.TITLE_SLIDE);
        LayoutAnalysis sectionHeader = layout("L2", SemanticType.SECTION_HEADER);

        // When
        Optional<LayoutAnalysis> result = fallback.findUltimateFallback(
            List.of(content, sectionHeader, titleSlide), SlideType.TITLE);

        // Then
        assertThat(result).contains(titleSlide);
    }

    @Test
    void findUltimateFallback_title_thenSectionHeader_thenBaseDefault() {
        // Given
        LayoutAnalysis content = layout("L0", SemanticType.CONTENT);
        LayoutAnalysis sectionHeader = layout("L2", SemanticType.SECTION_HEADER);

        // When & Then
        assertThat(fallback.findUltimateFallback(List.of(content, sectionHeader), SlideType.TITLE))
            .contains(sectionHeader);
        assertThat(fallback.findUltimateFallback(List.of(content), SlideType.TITLE))
            .contains(content);
    }

    @Test
    void findUltimateFallback_content_prefersContent() {
        // Given
        LayoutAnalysis content = layout("L1", SemanticType.CONTENT);
        LayoutAnalysis twoColumn = layout("L2", SemanticType.TWO_COLUMN);

        // When
        Optional<LayoutAnalysis> result = fallback.findUltimateFallback(
            List.of(content, twoColumn), SlideType.CONTENT);

        // Then
        assertThat(result).contains(content);
    }

    @Test
    void findUltimateFallback_content_thenTwoColumn_thenContentWithMedia() {
        // Given
        LayoutAnalysis twoColumn = layout("L2", SemanticType.TWO_COLUMN);
        LayoutAnalysis withMedia = layout("L3", SemanticType.CONTENT_WITH_MEDIA);
        List<LayoutAnalysis> layouts = List.of(twoColumn, withMedia);

        // When & Then
        assertThat(fallback.findUltimateFallback(layouts, SlideType.CONTENT)).contains(twoColumn);
        assertThat(fallback.findUltimateFallback(List.of(withMedia), SlideType.CONTENT)).contains(withMedia);
    }

    @Test
    void findUltimateFallback_content_noMatchingType_fallsBackToLayoutWithBody() {
        // Given
        LayoutAnalysis withBody = layoutWithBody("L1");
        List<LayoutAnalysis> layouts = List.of(withBody);

        // When
        Optional<LayoutAnalysis> result = fallback.findUltimateFallback(layouts, SlideType.CONTENT);

        // Then
        assertThat(result).contains(withBody);
    }

    @Test
    void findUltimateFallback_content_none_fallsBackToBaseDefault() {
        // Given
        LayoutAnalysis blank = layout("L1", SemanticType.BLANK);
        List<LayoutAnalysis> layouts = List.of(blank);

        // When
        Optional<LayoutAnalysis> result = fallback.findUltimateFallback(layouts, SlideType.CONTENT);

        // Then
        assertThat(result).contains(blank);
    }

    @Test
    void filterUsableForContent_removesNonContentLayouts() {
        // Given
        List<LayoutAnalysis> layouts = List.of(
            layout("L1", SemanticType.CONTENT),
            layout("L2", SemanticType.TWO_COLUMN),
            layout("L3", SemanticType.OUTLINE),
            layout("L4", SemanticType.TITLE_SLIDE),
            layout("L5", SemanticType.SECTION_HEADER),
            layout("L6", SemanticType.CUSTOM),
            layout("L7", SemanticType.BLANK),
            layout("L8", SemanticType.CONTENT_WITH_MEDIA));

        // When
        List<LayoutAnalysis> usable = fallback.filterUsableForContent(layouts);

        // Then
        assertThat(usable)
            .extracting(LayoutAnalysis::getLayoutId)
            .containsExactly("L1", "L2", "L8");
    }

    private static LayoutAnalysis layout(String id, SemanticType type) {
        return LayoutAnalysis.builder()
            .layoutId(id)
            .semanticType(type)
            .build();
    }

    private static LayoutAnalysis layoutWithBody(String id) {
        return LayoutAnalysis.builder()
            .layoutId(id)
            .semanticType(SemanticType.CUSTOM)
            .zones(List.of(Zone.builder().zoneType(ZoneType.BODY).build()))
            .build();
    }
}
