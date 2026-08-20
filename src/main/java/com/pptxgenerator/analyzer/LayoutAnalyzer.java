package com.pptxgenerator.analyzer;

import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.SlideDimensions;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ContentCapacity;
import com.pptxgenerator.model.enums.SemanticType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class LayoutAnalyzer {

    private final ZoneIdentifier zoneIdentifier;
    private final BackgroundDetector backgroundDetector;
    private final SemanticZoneNamer semanticZoneNamer;
    private final ContentCapacityCalculator capacityCalculator;

    /**
     * Analyse tous les layouts du template
     */
    public List<LayoutAnalysis> analyze(PresentationMLPackage pptx, SlideDimensions dimensions) throws Docx4JException {
        log.info("Analyse des layouts...");

        List<LayoutAnalysis> layouts = new ArrayList<>();
        List<SlideLayoutPart> layoutParts = pptx.getParts().getParts().values().stream()
            .filter(SlideLayoutPart.class::isInstance)
            .map(SlideLayoutPart.class::cast)
            .toList();

        for (int i = 0; i < layoutParts.size(); i++) {
            SlideLayoutPart layoutPart = layoutParts.get(i);
            LayoutAnalysis layout = analyzeSingleLayout(layoutPart, i, dimensions);
            layouts.add(layout);
        }

        log.info("{} layouts analysés", layouts.size());
        return layouts;
    }

    private LayoutAnalysis analyzeSingleLayout(SlideLayoutPart layoutPart, int index, SlideDimensions dimensions) throws Docx4JException {
        String layoutName = layoutPart.getContents().getCSld().getName();

        // 1. Identification des zones
        List<Zone> zones = zoneIdentifier.identify(layoutPart, dimensions);

        // 2. Détection des zones background
        zones = backgroundDetector.detect(zones);

        // 3. Nommage sémantique (left_column, right_column, box_1, etc.)
        zones = semanticZoneNamer.nameZones(zones);

        // 4. Calcul de la capacité de contenu
        ContentCapacity capacity = capacityCalculator.calculate(zones);

        return LayoutAnalysis.builder()
            .layoutId("layout_" + index)
            .originalName(layoutName)
            .semanticType(SemanticType.PENDING) // Sera classifié par l'IA
            .description("") // Sera enrichi par l'IA
            .contentCapacity(capacity)
            .zones(zones)
            .build();
    }
}
