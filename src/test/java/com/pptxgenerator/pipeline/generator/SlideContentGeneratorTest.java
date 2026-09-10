package com.pptxgenerator.pipeline.generator;

import com.pptxgenerator.common.ai.AiCallExecutor;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.ZoneKeys;
import com.pptxgenerator.model.enums.ZoneType;
import com.pptxgenerator.model.enums.SemanticType;
import com.pptxgenerator.pipeline.assigner.model.ClassifiedLayout;
import com.pptxgenerator.pipeline.assigner.model.SlidePlanWithLayout;
import com.pptxgenerator.pipeline.generator.model.SlideContent;
import com.pptxgenerator.pipeline.planner.model.SlideType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SlideContentGenerator}: the deterministic OUTLINE and
 * SECTION_TRANSITION generation, and the AI path with its fallback.
 */
@ExtendWith(MockitoExtension.class)
class SlideContentGeneratorTest {

    @Mock
    AiCallExecutor aiCallExecutor;
    @Mock
    ContentPromptBuilder promptBuilder;

    SlideContentGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SlideContentGenerator(aiCallExecutor, promptBuilder);
    }

    @Test
    void generate_outlineSlide_buildsContentFromSectionTransitions() {
        // Given
        SlidePlanWithLayout transition1 = sectionTransition(1, "Introduction");
        SlidePlanWithLayout transition2 = sectionTransition(3, "Conclusion");
        SlidePlanWithLayout outline = outlineSlide(5,
            zone(0, ZoneType.TITLE),
            zone(1, ZoneType.LINE),
            zone(2, ZoneType.LINE),
            zone(3, ZoneType.WORD),
            zone(4, ZoneType.WORD));
        List<SlidePlanWithLayout> allSlides = List.of(transition1, outline, transition2);

        // When
        SlideContent result = generator.generate(outline, 1, allSlides, "fr", "PROFESSIONAL", false, null);

        // Then
        assertThat(result.getContent()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "title_0", "Sommaire",
            "line_1", "Introduction",
            "line_2", "Conclusion",
            "word_3", "01",
            "word_4", "02"));
    }

    @Test
    void generate_outlineSlide_bodyOnlyLayout_usesNumberedBodyList() {
        // Given: forme B - title + body, pas de line/word (layout "Titre et contenu")
        SlidePlanWithLayout transition = sectionTransition(1, "Introduction");
        SlidePlanWithLayout outline = outlineSlide(2,
            zone(0, ZoneType.TITLE),
            zone(1, ZoneType.BODY));
        List<SlidePlanWithLayout> allSlides = List.of(transition, outline);

        // When
        SlideContent result = generator.generate(outline, 1, allSlides, "fr", "PROFESSIONAL", false, null);

        // Then: liste numérotée dans le body, pas de zones word/line
        assertThat(result.getContent()).containsEntry("body_1", "- 01 - Introduction");    }

    @Test
    void generate_outlineSlide_linesOnlyLayout_embedsNumbersInText() {
        // Given: forme C - title + lines, pas de word (cas du template sans layout sommaire)
        SlidePlanWithLayout transition1 = sectionTransition(1, "Introduction");
        SlidePlanWithLayout transition2 = sectionTransition(3, "Conclusion");
        SlidePlanWithLayout outline = outlineSlide(2,
            zone(0, ZoneType.TITLE),
            zone(1, ZoneType.LINE),
            zone(2, ZoneType.LINE));
        List<SlidePlanWithLayout> allSlides = List.of(transition1, outline, transition2);

        // When
        SlideContent result = generator.generate(outline, 1, allSlides, "fr", "PROFESSIONAL", false, null);

        // Then: numéros intégrés au texte, faute de zone dédiée
        assertThat(result.getContent()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "title_0", "Sommaire",
            "line_1", "01 · Introduction",
            "line_2", "02 · Conclusion"));
    }

    @Test
    void generate_outlineSlide_sectionTitleEmpty_fallsBackToContentBriefThenPurpose() {
        // Given
        SlidePlanWithLayout transition = SlidePlanWithLayout.builder()
            .slideNumber(1)
            .slideType(SlideType.SECTION_TRANSITION)
            .purpose("purpose fallback")
            .build();
        SlidePlanWithLayout outline = outlineSlide(2,
            zone(0, ZoneType.TITLE),
            zone(1, ZoneType.LINE));
        List<SlidePlanWithLayout> allSlides = List.of(transition, outline);

        // When
        SlideContent result = generator.generate(outline, 1, allSlides, "fr", "PROFESSIONAL", false, null);

        // Then: forme C - une seule line, numéro intégré au texte
        assertThat(result.getContent()).containsEntry("line_1", "01 · purpose fallback");
    }

    @Test
    void generate_sectionTransition_mapsTitleNumberAndSubtitleHook() {
        // Given
        String context = "c".repeat(150);
        SlidePlanWithLayout slide = SlidePlanWithLayout.builder()
            .slideNumber(1)
            .slideType(SlideType.SECTION_TRANSITION)
            .sectionTitle("Partie 1")
            .sectionNumber(1)
            .detailedContext(context)
            .layout(layout(
                zone(0, ZoneType.TITLE),
                zone(1, ZoneType.WORD),
                zone(2, ZoneType.SUBTITLE)))
            .build();
        List<SlidePlanWithLayout> allSlides = List.of(slide);

        // When
        SlideContent result = generator.generate(slide, 0, allSlides, "fr", "PROFESSIONAL", false, null);

        // Then
        assertThat(result.getContent()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "title_0", "Partie 1",
            "word_1", "01",
            "subtitle_2", "c".repeat(97) + "..."));
    }

    @Test
    void generate_sectionTransition_secondTransition_incrementsSectionNumber() {
        // Given
        SlidePlanWithLayout first = sectionTransition(1, "Intro");
        SlidePlanWithLayout second = SlidePlanWithLayout.builder()
            .slideNumber(3)
            .slideType(SlideType.SECTION_TRANSITION)
            .sectionTitle("Deuxième")
            .layout(layout(zone(0, ZoneType.TITLE), zone(1, ZoneType.WORD)))
            .build();
        List<SlidePlanWithLayout> allSlides = List.of(first, second);

        // When
        SlideContent result = generator.generate(second, 1, allSlides, "fr", "PROFESSIONAL", false, null);

        // Then
        assertThat(result.getContent()).containsEntry("word_1", "02");
    }

    @Test
    void generate_contentSlide_callsAi() {
        // Given
        SlidePlanWithLayout slide = contentSlide(1, zone(0, ZoneType.BODY));
        SlideContent expected = SlideContent.builder()
            .content(Map.of("body_0", "text"))
            .build();
        when(promptBuilder.buildSystemPrompt()).thenReturn("system");
        when(promptBuilder.buildUserPrompt(any(), any(), any(), any(), any(), anyBoolean(), any()))
            .thenReturn("user");
        when(aiCallExecutor.call(any(), any(), any(), any(), eq(SlideContent.class))).thenReturn(expected);
        List<SlidePlanWithLayout> allSlides = List.of(slide);

        // When
        SlideContent result = generator.generate(slide, 0, allSlides, "fr", "PROFESSIONAL", false, null);

        // Then
        assertThat(result).isSameAs(expected);
    }

    @Test
    void generate_contentSlide_noLayoutZones_returnsFallback() {
        // Given
        SlidePlanWithLayout slide = SlidePlanWithLayout.builder()
            .slideNumber(1)
            .slideType(SlideType.CONTENT)
            .layout(layout())
            .build();
        List<SlidePlanWithLayout> allSlides = List.of(slide);

        // When
        SlideContent result = generator.generate(slide, 0, allSlides, "fr", "PROFESSIONAL", false, null);

        // Then
        assertThat(result.getContent()).isEmpty();
    }

    private static SlidePlanWithLayout sectionTransition(int number, String title) {
        return SlidePlanWithLayout.builder()
            .slideNumber(number)
            .slideType(SlideType.SECTION_TRANSITION)
            .sectionTitle(title)
            .build();
    }

    private static SlidePlanWithLayout outlineSlide(int number, Zone... zones) {
        return SlidePlanWithLayout.builder()
            .slideNumber(number)
            .slideType(SlideType.OUTLINE)
            .layout(layout(zones))
            .build();
    }

    private static SlidePlanWithLayout contentSlide(int number, Zone... zones) {
        return SlidePlanWithLayout.builder()
            .slideNumber(number)
            .slideType(SlideType.CONTENT)
            .purpose("purpose")
            .contentBrief("brief")
            .detailedContext("ctx")
            .layout(layout(zones))
            .build();
    }

    private static ClassifiedLayout layout(Zone... zones) {
        return ClassifiedLayout.builder()
            .layoutId("L1")
            .semanticType(SemanticType.CONTENT)
            .zones(List.of(zones))
            .build();
    }

    private static Zone zone(int id, ZoneType type) {
        return Zone.builder().zoneId(id).zoneType(type).build();
    }
}
