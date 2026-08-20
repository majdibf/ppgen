package com.pptxgenerator.analyzer;

import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.SlideDimensions;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class AIEnrichmentService {

    private final ZoneDescriptionEnricher zoneEnricher;
    private final LayoutClassificationEnricher layoutEnricher;

    /**
     * Enrichit les layouts avec descriptions et classifications via IA
     */
    public List<LayoutAnalysis> enrich(List<LayoutAnalysis> layouts, SlideDimensions dimensions) {
        log.info("Enrichissement par IA de {} layouts...", layouts.size());

        // Step 1 : Enrichir les descriptions de zones
        layouts = zoneEnricher.enrich(layouts, dimensions);

        // Step 2 : Classifier les layouts et enrichir leurs descriptions
        layouts = layoutEnricher.enrich(layouts, dimensions);

        log.info("Enrichissement terminé");
        return layouts;
    }
}
