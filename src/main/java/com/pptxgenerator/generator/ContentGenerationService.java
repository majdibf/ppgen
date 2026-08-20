package com.pptxgenerator.generator;

import com.pptxgenerator.assigner.model.PlanWithLayouts;
import com.pptxgenerator.assigner.model.SlidePlanWithLayout;
import com.pptxgenerator.generator.model.ContentGenerationWarning;
import com.pptxgenerator.generator.model.GeneratedContent;
import com.pptxgenerator.generator.model.SlideContent;
import com.pptxgenerator.generator.parallel.ParallelContentGenerator;
import com.pptxgenerator.generator.validation.ContentValidator;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ContentGenerationService {

    private final ParallelContentGenerator parallelGenerator;
    private final ContentValidator validator;

    /**
     * Génère le contenu de toutes les slides du plan enrichi.
     */
    public GeneratedContent generateContent(PlanWithLayouts planWithLayouts,
                                             String language,
                                             String tone,
                                             boolean webSearch) {

        log.info("Step 3: Génération du contenu pour {} slides", planWithLayouts.getPlanWithLayouts().getTotalSlides());

        List<SlidePlanWithLayout> slides = planWithLayouts.getPlanWithLayouts().getSlides();

        // 1. Génération parallèle
        List<SlideContent> contents = parallelGenerator.generateAll(slides, language, tone, webSearch);

        // 2. Assembler les slides avec leur contenu
        List<GeneratedContent.SlideWithContent> slidesWithContent = new ArrayList<>();
        for (int i = 0; i < slides.size(); i++) {
            SlidePlanWithLayout slide = slides.get(i);
            SlideContent content = contents.get(i);

            slidesWithContent.add(GeneratedContent.SlideWithContent.builder()
                .slideNumber(slide.getSlideNumber())
                .slideType(slide.getSlideType().getValue())
                .purpose(slide.getPurpose())
                .contentBrief(slide.getContentBrief())
                .detailedContext(slide.getDetailedContext())
                .layout(slide.getLayout())
                .content(content)
                .build());
        }

        // 3. Validation post-génération (R1-R8)
        List<ContentGenerationWarning> warnings = validator.validateAndFix(slidesWithContent);

        // 4. Construire le résultat final
        GeneratedContent result = GeneratedContent.builder()
            .generatedContent(GeneratedContent.GeneratedContentData.builder()
                .title(planWithLayouts.getPlanWithLayouts().getTitle())
                .totalSlides(planWithLayouts.getPlanWithLayouts().getTotalSlides())
                .slides(slidesWithContent)
                .build())
            .warnings(warnings)
            .build();

        log.info("Step 3 terminé: {} slides générées, {} warnings", slides.size(), warnings.size());
        return result;
    }
}
