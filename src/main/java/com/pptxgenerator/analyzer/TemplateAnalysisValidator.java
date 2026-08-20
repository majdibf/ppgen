package com.pptxgenerator.analyzer;

import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.TemplateAnalysis;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class TemplateAnalysisValidator {

    /**
     * Valide la cohérence minimale d'une analyse de template.
     */
    public void validate(TemplateAnalysis analysis) {
        if (analysis == null) {
            throw new IllegalStateException("L'analyse du template est nulle");
        }

        if (analysis.getSlideDimensions() == null
            || analysis.getSlideDimensions().getWidth() == null
            || analysis.getSlideDimensions().getHeight() == null
            || analysis.getSlideDimensions().getWidth() <= 0
            || analysis.getSlideDimensions().getHeight() <= 0) {
            throw new IllegalStateException("Dimensions de slide invalides");
        }

        if (analysis.getLayouts() == null || analysis.getLayouts().isEmpty()) {
            throw new IllegalStateException("Aucun layout détecté dans le template");
        }

        for (LayoutAnalysis layout : analysis.getLayouts()) {
            if (layout.getZones() == null) {
                throw new IllegalStateException("Zones nulles pour le layout " + layout.getLayoutId());
            }
        }

        if (analysis.getTheme() == null) {
            log.warn("Aucun thème détecté pour le template");
        }

        if (analysis.getStructuralElements() == null) {
            log.warn("Aucun élément structurel détecté pour le template");
        }
    }
}
