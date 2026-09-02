package com.pptxgenerator.pipeline.renderer;

import com.pptxgenerator.model.TemplateAnalysis;
import com.pptxgenerator.pipeline.assigner.model.ClassifiedLayout;
import com.pptxgenerator.pipeline.generator.model.GeneratedContent;
import com.pptxgenerator.pipeline.generator.model.SlideContent;
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
 * Moteur de rendu principal : orchestre la préparation du template, la résolution
 * des layouts, la création des slides et l'injection du contenu.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PptxRenderEngine {

    private final SlideFactory slideFactory;
    private final PlaceholderMapper mapper;

    /**
     * Rend le PPTX final.
     *
     * @param templatePath      chemin du template source
     * @param templateAnalysis  analyse du template (layouts disponibles)
     * @param generatedContent  contenu généré (layout + texte par slide)
     * @param outputPath        chemin de destination du PPTX produit
     * @return résultat du rendu (fichier, temps, warnings)
     * @throws Exception si le template ne peut pas être chargé ou sauvegardé
     */
    public RenderResult render(String templatePath,
                               TemplateAnalysis templateAnalysis,
                               GeneratedContent generatedContent,
                               String outputPath) throws Exception {

        log.info("Starting PPTX rendering.");
        long startTime = System.currentTimeMillis();

        // 1. Chargement et purge du template
        PresentationMLPackage pptx = PresentationMLPackage.load(new File(templatePath));
        slideFactory.purgeExistingSlides(pptx);

        // 2. Récupération des slides à générer
        List<GeneratedContent.SlideWithContent> slides = generatedContent.getGeneratedContent().getSlides();
        List<RenderWarning> allWarnings = new ArrayList<>();

        // 3. Vérification du nombre de slides
        Integer totalSlides = generatedContent.getGeneratedContent().getTotalSlides();
        if (totalSlides != null && slides.size() != totalSlides) {
            allWarnings.add(RenderWarning.builder()
                    .code("SLIDE_CONTENT_MISMATCH")
                    .message("Declared total_slides (" + totalSlides
                            + ") does not match rendered slide count (" + slides.size() + ").")
                    .build());
            log.warn("Slide count mismatch; rendering will skip unmatched slides.");
        }

        // 4. Rendu de chaque slide
        for (int i = 0; i < slides.size(); i++) {
            renderSlide(pptx, i, slides.get(i), templateAnalysis, allWarnings);
        }

        // 5. Sauvegarde
        File outputFile = new File(outputPath);
        pptx.save(outputFile);

        // 6. Résultat
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

    /**
     * Rend une slide individuelle.
     */
    private void renderSlide(PresentationMLPackage pptx,
                             int index,
                             GeneratedContent.SlideWithContent slideWithContent,
                             TemplateAnalysis templateAnalysis,
                             List<RenderWarning> warnings) {

        int slideNumber = slideWithContent.getSlideNumber() != null
                ? slideWithContent.getSlideNumber() : index + 1;

        try {
            // 1. Vérification du contenu
            SlideContent content = slideWithContent.getContent();
            if (content == null) {
                warnings.add(RenderWarning.builder()
                        .code("SLIDE_CONTENT_MISSING")
                        .message("No generated content for slide " + slideNumber + ".")
                        .affectedSlides(List.of(slideNumber))
                        .build());
                return;
            }

            // 2. Vérification du layout
            ClassifiedLayout layout = slideWithContent.getLayout();
            if (layout == null) {
                warnings.add(RenderWarning.builder()
                        .code("LAYOUT_MISSING")
                        .message("No layout assigned to slide " + slideNumber + ".")
                        .affectedSlides(List.of(slideNumber))
                        .build());
                return;
            }

            // 3. Résolution du layout
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

            // 4. Création de la slide
            SlidePart slidePart = slideFactory.createSlide(pptx, layoutPartOpt.get(), index);

            // 5. Injection du contenu (via PlaceholderMapper)
            Map<String, String> zoneText = content.getContent();
            warnings.addAll(mapper.inject(
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