package com.pptxgenerator.pipeline.generator;

import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ZoneType;
import com.pptxgenerator.model.enums.SemanticType;
import com.pptxgenerator.pipeline.assigner.model.ClassifiedLayout;
import com.pptxgenerator.pipeline.assigner.model.PlanWithLayouts;
import com.pptxgenerator.pipeline.assigner.model.SlidePlanWithLayout;
import com.pptxgenerator.pipeline.generator.model.ContentGenerationWarning;
import com.pptxgenerator.pipeline.generator.model.GeneratedContent;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ContentGenerationService} orchestration.
 */
@ExtendWith(MockitoExtension.class)
class ContentGenerationServiceTest {

    @Mock
    SlideContentGenerator slideContentGenerator;
    @Mock
    ContentValidator validator;

    ContentGenerationService service;

    @BeforeEach
    void setUp() {
        service = new ContentGenerationService(slideContentGenerator, validator);
        lenient().when(validator.validateAndFix(anyList())).thenReturn(List.of());
    }

    @Test
    void generateContent_delegatesToGenerator_perSlide_andBuildsResult() {
        // Given
        SlidePlanWithLayout slide = contentSlide(1);
        PlanWithLayouts plan = PlanWithLayouts.builder()
            .title("T")
            .totalSlides(1)
            .slides(List.of(slide))
            .warnings(List.of())
            .build();
        SlideContent content = SlideContent.builder().content(Map.of("body_0", "text")).build();
        when(slideContentGenerator.generate(any(), anyInt(), anyList(), anyString(), anyString(), anyBoolean(), any()))
            .thenReturn(content);

        // When
        GeneratedContent result = service.generateContent(plan, "fr", "PROFESSIONAL", false, null);

        // Then
        assertThat(result.getWarnings()).isEmpty();
        GeneratedContent.SlideWithContent slideResult = result.getGeneratedContent().getSlides().get(0);
        assertThat(slideResult.getSlideNumber()).isEqualTo(1);
        assertThat(slideResult.getContent()).isSameAs(content);
    }

    @Test
    void generateContent_generatorThrows_producesFallbackContent() {
        // Given
        SlidePlanWithLayout slide = contentSlide(1);
        PlanWithLayouts plan = PlanWithLayouts.builder()
            .title("T")
            .totalSlides(1)
            .slides(List.of(slide))
            .warnings(List.of())
            .build();
        when(slideContentGenerator.generate(any(), anyInt(), anyList(), anyString(), anyString(), anyBoolean(), any()))
            .thenThrow(new RuntimeException("boom"));

        // When
        GeneratedContent result = service.generateContent(plan, "fr", "PROFESSIONAL", false, null);

        // Then
        assertThat(result.getGeneratedContent().getSlides().get(0).getContent().getContent())
            .containsEntry("body_0", "Content to be generated");
    }

    @Test
    void generateContent_collectsValidatorWarnings() {
        // Given
        SlidePlanWithLayout slide = contentSlide(1);
        PlanWithLayouts plan = PlanWithLayouts.builder()
            .title("T")
            .totalSlides(1)
            .slides(List.of(slide))
            .warnings(List.of())
            .build();
        when(slideContentGenerator.generate(any(), anyInt(), anyList(), anyString(), anyString(), anyBoolean(), any()))
            .thenReturn(SlideContent.builder().content(Map.of()).build());
        List<ContentGenerationWarning> warnings = List.of(ContentGenerationWarning.builder()
            .code("EMPTY_CONTENT")
            .message("warning")
            .affectedSlides(List.of(1))
            .build());
        when(validator.validateAndFix(anyList())).thenReturn(warnings);

        // When
        GeneratedContent result = service.generateContent(plan, "fr", "PROFESSIONAL", false, null);

        // Then
        verify(validator).validateAndFix(result.getGeneratedContent().getSlides());
        assertThat(result.getWarnings()).isSameAs(warnings);
    }

    @Test
    void generateContent_propagatesTitleTotalSlidesAndSlideMetadata() {
        // Given
        SlidePlanWithLayout slide = contentSlide(1);
        PlanWithLayouts plan = PlanWithLayouts.builder()
            .title("Mon deck")
            .totalSlides(1)
            .slides(List.of(slide))
            .warnings(List.of())
            .build();
        when(slideContentGenerator.generate(any(), anyInt(), anyList(), anyString(), anyString(), anyBoolean(), any()))
            .thenReturn(SlideContent.builder().content(Map.of("body_0", "text")).build());

        // When
        GeneratedContent result = service.generateContent(plan, "fr", "PROFESSIONAL", false, null);

        // Then
        assertThat(result.getGeneratedContent().getTitle()).isEqualTo("Mon deck");
        assertThat(result.getGeneratedContent().getTotalSlides()).isEqualTo(1);
        GeneratedContent.SlideWithContent slideResult = result.getGeneratedContent().getSlides().get(0);
        assertThat(slideResult.getSlideType()).isEqualTo("content");
        assertThat(slideResult.getLayout().getLayoutId()).isEqualTo("L1");
    }

    private static SlidePlanWithLayout contentSlide(int number) {
        return SlidePlanWithLayout.builder()
            .slideNumber(number)
            .slideType(SlideType.CONTENT)
            .purpose("purpose")
            .contentBrief("brief")
            .detailedContext("ctx")
            .layout(ClassifiedLayout.builder()
                .layoutId("L1")
                .semanticType(SemanticType.CONTENT)
                .zones(List.of(Zone.builder().zoneId(0).zoneType(ZoneType.BODY).build()))
                .build())
            .build();
    }
}
