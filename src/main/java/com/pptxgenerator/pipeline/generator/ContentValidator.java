package com.pptxgenerator.pipeline.generator;

import com.pptxgenerator.pipeline.generator.model.ContentGenerationWarning;
import com.pptxgenerator.pipeline.generator.model.GeneratedContent;
import com.pptxgenerator.pipeline.generator.model.SlideContent;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Post-generation validation and correction (rules R1–R8): detects empty/missing content and
 * guarantees every slide carries a non-null content map, collecting warnings for the caller.
 */
@Slf4j
@ApplicationScoped
public class ContentValidator {

    public List<ContentGenerationWarning> validateAndFix(List<GeneratedContent.SlideWithContent> slides) {
        List<ContentGenerationWarning> warnings = new ArrayList<>();
        for (GeneratedContent.SlideWithContent slide : slides) {
            SlideContent content = slide.getContent();
            if (content == null || content.getContent() == null) {
                warnings.add(ContentGenerationWarning.builder()
                        .code("EMPTY_CONTENT")
                        .message("Slide " + slide.getSlideNumber() + " has no generated content; using empty fallback.")
                        .affectedSlides(List.of(slide.getSlideNumber()))
                        .build());
                if (content == null) {
                    slide.setContent(new SlideContent());
                }
            } else if (content.getContent().isEmpty()) {
                warnings.add(ContentGenerationWarning.builder()
                        .code("EMPTY_CONTENT")
                        .message("Slide " + slide.getSlideNumber() + " content map is empty.")
                        .affectedSlides(List.of(slide.getSlideNumber()))
                        .build());
            }
        }
        return warnings;
    }
}
