package com.pptxgenerator.renderer;

import com.pptxgenerator.assigner.model.ClassifiedLayout;
import com.pptxgenerator.assigner.model.PlanWithLayouts;
import com.pptxgenerator.assigner.model.SlidePlanWithLayout;
import com.pptxgenerator.generator.model.GeneratedContent;
import com.pptxgenerator.generator.model.SlideContent;
import com.pptxgenerator.model.TemplateAnalysis;
import com.pptxgenerator.renderer.building.LayoutResolver;
import com.pptxgenerator.renderer.building.SlideBuilder;
import com.pptxgenerator.renderer.injection.PlaceholderInjector;
import com.pptxgenerator.renderer.injection.PlaceholderMapper;
import com.pptxgenerator.renderer.model.RenderResult;
import com.pptxgenerator.renderer.model.RenderWarning;
import com.pptxgenerator.renderer.preparation.TemplatePreparator;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.pptx4j.pml.Shape;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PptxRenderEngine {

    private final TemplatePreparator preparator;
    private final LayoutResolver layoutResolver;
    private final SlideBuilder slideBuilder;
    private final PlaceholderMapper placeholderMapper;
    private final PlaceholderInjector injector;

    /**
     * Génère le fichier PPTX final.
     */
    public RenderResult render(String templatePath, TemplateAnalysis templateAnalysis,
                                PlanWithLayouts planWithLayouts, GeneratedContent generatedContent,
                                String outputPath) throws Exception {
        log.info("Démarrage du rendu PPTX");
        long startTime = System.currentTimeMillis();

        // 1. Charger le template
        PresentationMLPackage pptx = PresentationMLPackage.load(new File(templatePath));

        // 2. Purger les slides existantes
        preparator.purgeExistingSlides(pptx);

        List<RenderWarning> allWarnings = new ArrayList<>();
        List<SlidePlanWithLayout> slides = planWithLayouts.getPlanWithLayouts().getSlides();

        // 3. Générer chaque slide
        for (int i = 0; i < slides.size(); i++) {
            SlidePlanWithLayout slidePlan = slides.get(i);
            SlideContent content = generatedContent.getGeneratedContent().getSlides().get(i).getContent();
            ClassifiedLayout layout = slidePlan.getLayout();

            try {
                // Résoudre le layout
                Optional<SlideLayoutPart> layoutPartOpt = layoutResolver.resolve(pptx, layout.getLayoutId(), templateAnalysis);
                if (layoutPartOpt.isEmpty()) {
                    allWarnings.add(RenderWarning.builder()
                        .code("LAYOUT_NOT_FOUND")
                        .message("Layout " + layout.getLayoutId() + " non trouvé, utilisation du premier layout disponible")
                        .affectedSlides(List.of(slidePlan.getSlideNumber()))
                        .build());
                    continue;
                }

                // Créer la slide
                SlidePart slidePart = slideBuilder.createSlide(pptx, layoutPartOpt.get(), i);

                // Mapper les placeholders
                Map<String, Shape> placeholderMapping = placeholderMapper.mapPlaceholders(slidePart, layout.getZones());

                // Injecter le contenu
                List<RenderWarning> slideWarnings = injector.inject(slidePart, content, layoutPartOpt.get(), layout.getZones());
                allWarnings.addAll(slideWarnings);

                log.debug("Slide {} générée avec succès", slidePlan.getSlideNumber());

            } catch (Exception e) {
                log.error("Erreur génération slide {}", slidePlan.getSlideNumber(), e);
                allWarnings.add(RenderWarning.builder()
                    .code("SLIDE_GENERATION_FAILED")
                    .message("Erreur lors de la génération de la slide " + slidePlan.getSlideNumber() + ": " + e.getMessage())
                    .affectedSlides(List.of(slidePlan.getSlideNumber()))
                    .build());
            }
        }

        // 4. Sauvegarder
        File outputFile = new File(outputPath);
        pptx.save(outputFile);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Rendu PPTX terminé en {}ms, {} slides, {} warnings", duration, slides.size(), allWarnings.size());

        return RenderResult.builder()
            .outputFile(outputFile)
            .totalSlides(slides.size())
            .generationTimeMs(duration)
            .warnings(allWarnings)
            .build();
    }
}
