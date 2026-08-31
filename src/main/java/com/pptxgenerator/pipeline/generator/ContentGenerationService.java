package com.pptxgenerator.pipeline.generator;

import com.pptxgenerator.pipeline.assigner.model.PlanWithLayouts;
import com.pptxgenerator.pipeline.assigner.model.SlidePlanWithLayout;
import com.pptxgenerator.pipeline.generator.model.ContentGenerationWarning;
import com.pptxgenerator.pipeline.generator.model.GeneratedContent;
import com.pptxgenerator.pipeline.generator.model.SlideContent;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates content generation (Step 4): iterates over slides sequentially,
 * delegates to {@link SlideContentGenerator}, and runs post-generation validation.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ContentGenerationService {

    private final SlideContentGenerator slideContentGenerator;
    private final ContentValidator validator;

    @ConfigProperty(name = "app.ai.model-id", defaultValue = "llama-3.3-70b-versatile")
    String modelId;

    public GeneratedContent generateContent(PlanWithLayouts planWithLayouts,
                                            String language,
                                            String tone,
                                            boolean webSearch) {
        return generateContent(planWithLayouts, modelId, language, tone, webSearch);
    }

    public GeneratedContent generateContent(PlanWithLayouts planWithLayouts,
                                            String modelId,
                                            String language,
                                            String tone,
                                            boolean webSearch) {
        log.info("Step 4: Initializing text generation for {} slides", planWithLayouts.getTotalSlides());

        List<SlidePlanWithLayout> slides = planWithLayouts.getSlides();
        List<GeneratedContent.SlideWithContent> slidesWithContent = new ArrayList<>();

        for (int i = 0; i < slides.size(); i++) {
            SlidePlanWithLayout slide = slides.get(i);

            SlideContent content = generateWithFallback(slide, i, slides, modelId, language, tone, webSearch);

            slidesWithContent.add(GeneratedContent.SlideWithContent.builder()
                    .slideNumber(slide.getSlideNumber())
                    .slideType(slide.getSlideType() != null ? slide.getSlideType().getValue() : null)
                    .purpose(slide.getPurpose())
                    .contentBrief(slide.getContentBrief())
                    .detailedContext(slide.getDetailedContext())
                    .layout(slide.getLayout())
                    .content(content)
                    .build());
        }

        List<ContentGenerationWarning> warnings = validator.validateAndFix(slidesWithContent);

        log.info("Step 4 completed: {} slides generated, {} warnings generated", slides.size(), warnings.size());

        GeneratedContent.GeneratedContentData data = GeneratedContent.GeneratedContentData.builder()
                .title(planWithLayouts.getTitle())
                .totalSlides(planWithLayouts.getTotalSlides())
                .slides(slidesWithContent)
                .build();

        return GeneratedContent.builder()
                .generatedContent(data)
                .warnings(warnings)
                .build();
    }

    private SlideContent generateWithFallback(SlidePlanWithLayout slide,
                                              int slideIndex,
                                              List<SlidePlanWithLayout> allSlides,
                                              String modelId,
                                              String language,
                                              String tone,
                                              boolean webSearch) {
        try {
            return slideContentGenerator.generate(slide, slideIndex, allSlides, modelId, language, tone, webSearch);
        } catch (Exception e) {
            log.error("Failed to generate slide {}: {}", slide.getSlideNumber(), e.getMessage());
            return createFallbackContent(slide);
        }
    }

    private SlideContent createFallbackContent(SlidePlanWithLayout slide) {
        SlideContent content = new SlideContent();
        java.util.Map<String, String> map = new java.util.HashMap<>();
        if (slide != null && slide.getLayout() != null && slide.getLayout().getZones() != null) {
            for (com.pptxgenerator.model.Zone zone : slide.getLayout().getZones()) {
                map.put(com.pptxgenerator.model.ZoneKeys.key(zone), "Content to be generated");
            }
        }
        content.setContent(map);
        return content;
    }
}
