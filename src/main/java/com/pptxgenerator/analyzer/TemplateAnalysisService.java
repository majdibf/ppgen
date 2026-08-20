package com.pptxgenerator.analyzer;

import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.SlideDimensions;
import com.pptxgenerator.model.StructuralElements;
import com.pptxgenerator.model.TemplateAnalysis;
import com.pptxgenerator.model.Theme;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.pptx4j.pml.Presentation;

import java.util.List;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TemplateAnalysisService {

    private final ThemeExtractor themeExtractor;
    private final LayoutAnalyzer layoutAnalyzer;
    private final StructuralElementsDetector structuralDetector;
    private final AIEnrichmentService aiEnrichmentService;
    private final TemplateAnalysisValidator validator;

    /**
     * Analyse complète d'un template PowerPoint
     */
    public TemplateAnalysis analyze(PresentationMLPackage pptx) throws Docx4JException {
        log.info("Démarrage de l'analyse du template");
        long startTime = System.currentTimeMillis();

        // 1. Extraction des dimensions
        SlideDimensions dimensions = extractDimensions(pptx);

        // 2. Extraction du thème
        Theme theme = themeExtractor.extract(pptx);

        // 3. Analyse des layouts
        List<LayoutAnalysis> layouts = layoutAnalyzer.analyze(pptx, dimensions);

        // 4. Enrichissement par IA
        layouts = aiEnrichmentService.enrich(layouts, dimensions);

        // 5. Détection des éléments structurels
        StructuralElements structuralElements = structuralDetector.detect(pptx);

        // 6. Construction du résultat
        TemplateAnalysis analysis = TemplateAnalysis.builder()
            .slideDimensions(dimensions)
            .theme(theme)
            .layouts(layouts)
            .structuralElements(structuralElements)
            .build();

        // 7. Validation
        validator.validate(analysis);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Analyse terminée en {}ms, {} layouts détectés", duration, layouts.size());

        return analysis;
    }

    private SlideDimensions extractDimensions(PresentationMLPackage pptx) throws Docx4JException {
        Presentation presentation = pptx.getMainPresentationPart().getContents();
        return SlideDimensions.builder()
            .width((long) presentation.getSldSz().getCx())
            .height((long) presentation.getSldSz().getCy())
            .unit("EMU")
            .build();
    }
}
