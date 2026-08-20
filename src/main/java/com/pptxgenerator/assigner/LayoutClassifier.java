package com.pptxgenerator.assigner;

import com.pptxgenerator.assigner.model.ClassifiedLayout;
import com.pptxgenerator.model.LayoutAnalysis;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Convertit les layouts de l'analyzer (com.pptxgenerator.model.LayoutAnalysis)
 * en layouts classifiés consommés par le module d'assignation (com.pptxgenerator.assigner.model.ClassifiedLayout).
 */
@Slf4j
@ApplicationScoped
public class LayoutClassifier {

    public List<ClassifiedLayout> classify(List<LayoutAnalysis> layouts) {
        return layouts.stream()
            .map(this::classify)
            .toList();
    }

    private ClassifiedLayout classify(LayoutAnalysis layout) {
        return ClassifiedLayout.builder()
            .layoutId(layout.getLayoutId())
            .originalName(layout.getOriginalName())
            .semanticType(layout.getSemanticType())
            .description(layout.getDescription())
            .contentCapacity(layout.getContentCapacity())
            .zones(layout.getZones())
            .build();
    }
}
