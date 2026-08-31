package com.pptxgenerator.pipeline.renderer;

import com.pptxgenerator.model.TemplateAnalysis;
import com.pptxgenerator.pipeline.assigner.model.ClassifiedLayout;
import com.pptxgenerator.pipeline.generator.model.GeneratedContent;
import com.pptxgenerator.pipeline.generator.model.SlideContent;
import com.pptxgenerator.pipeline.renderer.PlaceholderInjector;
import com.pptxgenerator.pipeline.renderer.model.RenderResult;
import com.pptxgenerator.pipeline.renderer.model.RenderWarning;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Top-level renderer: orchestrates template preparation, per-slide layout resolution + slide
 * creation (via {@link SlideFactory}), content injection, then persists the resulting PPTX.
 *
 * <p>The renderer only needs the {@link GeneratedContent} (which already embeds each slide's
 * assigned layout and text) plus the {@link TemplateAnalysis} used to resolve layouts.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PptxRenderEngine {

    private final SlideFactory slideFactory;
    private final PlaceholderInjector injector;

    /**
     * Renders the final PPTX.
     *
     * @param templatePath     path to the source template
     * @param templateAnalysis analysis describing the template's layouts
     * @param generatedContent generated content keyed by slide/zone (embeds layout + text)
     * @param outputPath       destination path for the produced PPTX
     * @return rendering result (output file, timing, warnings)
     * @throws Exception if the template cannot be loaded or the package cannot be saved
     */
    public RenderResult render(String templatePath,
                               TemplateAnalysis templateAnalysis,
                               GeneratedContent generatedContent,
                               String outputPath) throws Exception {

        log.info("Starting PPTX rendering.");
        long startTime = System.currentTimeMillis();

        PresentationMLPackage pptx = PresentationMLPackage.load(new File(templatePath));
        slideFactory.purgeExistingSlides(pptx);

        List<GeneratedContent.SlideWithContent> slides = generatedContent.getGeneratedContent().getSlides();
        List<RenderWarning> allWarnings = new ArrayList<>();

        Integer totalSlides = generatedContent.getGeneratedContent().getTotalSlides();
        if (totalSlides != null && slides.size() != totalSlides) {
            allWarnings.add(RenderWarning.builder()
                    .code("SLIDE_CONTENT_MISMATCH")
                    .message("Declared total_slides (" + totalSlides
                            + ") does not match rendered slide count (" + slides.size() + ").")
                    .build());
            log.warn("Slide count mismatch; rendering will skip unmatched slides.");
        }

        for (int i = 0; i < slides.size(); i++) {
            renderSlide(pptx, i, slides.get(i), templateAnalysis, allWarnings);
        }

        File outputFile = new File(outputPath);
        pptx.save(outputFile);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Rendering finished in {} ms: {} slide(s), {} warning(s).",
                duration, slides.size(), allWarnings.size());

        return RenderResult.builder()
                .outputFile(outputFile)
                .totalSlides(slides.size())
                .generationTimeMs(duration)
                .warnings(allWarnings)
                .build();
    }

    private void renderSlide(PresentationMLPackage pptx,
                             int index,
                             GeneratedContent.SlideWithContent slideWithContent,
                             TemplateAnalysis templateAnalysis,
                             List<RenderWarning> warnings) {
        int slideNumber = slideWithContent.getSlideNumber() != null
                ? slideWithContent.getSlideNumber() : index + 1;
        try {
            SlideContent content = slideWithContent.getContent();
            if (content == null) {
                warnings.add(RenderWarning.builder()
                        .code("SLIDE_CONTENT_MISSING")
                        .message("No generated content for slide " + slideNumber + ".")
                        .affectedSlides(List.of(slideNumber))
                        .build());
                return;
            }
            ClassifiedLayout layout = slideWithContent.getLayout();
            if (layout == null) {
                warnings.add(RenderWarning.builder()
                        .code("LAYOUT_MISSING")
                        .message("No layout assigned to slide " + slideNumber + ".")
                        .affectedSlides(List.of(slideNumber))
                        .build());
                return;
            }

            Optional<SlideLayoutPart> layoutPartOpt = slideFactory.resolveLayout(
                    pptx, layout.getLayoutId(), templateAnalysis);
            if (layoutPartOpt.isEmpty()) {
                warnings.add(RenderWarning.builder()
                        .code("LAYOUT_NOT_FOUND")
                        .message("Layout " + layout.getLayoutId() + " not found.")
                        .affectedSlides(List.of(slideNumber))
                        .build());
                return;
            }

            SlidePart slidePart = slideFactory.createSlide(pptx, layoutPartOpt.get(), index);
            Map<String, String> zoneText = content.getContent();
            warnings.addAll(injector.inject(
                    slidePart, zoneText, layoutPartOpt.get(), layout.getZones()));

            log.debug("Slide {} rendered successfully.", slideNumber);
        } catch (Exception e) {
            log.error("Failed to render slide {}.", slideNumber, e);
            warnings.add(RenderWarning.builder()
                    .code("SLIDE_GENERATION_FAILED")
                    .message("Error rendering slide " + slideNumber + ": " + e.getMessage())
                    .affectedSlides(List.of(slideNumber))
                    .build());
        }
    }
}
