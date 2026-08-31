package com.pptxgenerator.pipeline.assigner;

import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.enums.SemanticType;
import com.pptxgenerator.model.enums.ZoneType;
import com.pptxgenerator.pipeline.planner.model.SlideType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Fallback strategies for layout assignment: selects the most appropriate layout when the
 * deterministic or AI assignment cannot be applied, and filters out layouts that must not be
 * used for narrative content.
 */
@Slf4j
@ApplicationScoped
public class FallbackAssignment {

    private static final Set<SemanticType> EXCLUDED_CONTENT_TYPES = Set.of(
            SemanticType.OUTLINE,
            SemanticType.TITLE_SLIDE,
            SemanticType.SECTION_HEADER,
            SemanticType.CUSTOM,
            SemanticType.BLANK
    );

    public Optional<LayoutAnalysis> findUltimateFallback(List<LayoutAnalysis> allLayouts, SlideType slideType) {
        if (allLayouts.isEmpty()) {
            return Optional.empty();
        }
        LayoutAnalysis baseDefault = allLayouts.get(0);
        return switch (slideType) {
            case TITLE -> findFirstByType(allLayouts, SemanticType.TITLE_SLIDE)
                    .or(() -> findFirstByType(allLayouts, SemanticType.SECTION_HEADER))
                    .or(() -> Optional.of(baseDefault));
            case SECTION_TRANSITION -> findFirstByType(allLayouts, SemanticType.SECTION_HEADER)
                    .or(() -> findFirstByType(allLayouts, SemanticType.TITLE_SLIDE))
                    .or(() -> Optional.of(baseDefault));
            case OUTLINE -> findFirstByType(allLayouts, SemanticType.OUTLINE)
                    .or(() -> findFirstByType(allLayouts, SemanticType.CONTENT))
                    .or(() -> findLayoutWithBody(allLayouts))
                    .or(() -> Optional.of(baseDefault));
            case CONTENT -> findFirstByType(allLayouts, SemanticType.CONTENT)
                    .or(() -> findFirstByType(allLayouts, SemanticType.TWO_COLUMN))
                    .or(() -> findFirstByType(allLayouts, SemanticType.CONTENT_WITH_MEDIA))
                    .or(() -> findLayoutWithBody(allLayouts))
                    .or(() -> Optional.of(baseDefault));
        };
    }

    public List<LayoutAnalysis> filterUsableForContent(List<LayoutAnalysis> layouts) {
        return layouts.stream()
                .filter(l -> !EXCLUDED_CONTENT_TYPES.contains(l.getSemanticType()))
                .toList();
    }

    private Optional<LayoutAnalysis> findFirstByType(List<LayoutAnalysis> layouts, SemanticType type) {
        return layouts.stream()
                .filter(l -> l.getSemanticType() == type)
                .findFirst();
    }

    private Optional<LayoutAnalysis> findLayoutWithBody(List<LayoutAnalysis> layouts) {
        return layouts.stream()
                .filter(l -> l.getZones() != null
                        && l.getZones().stream().anyMatch(z -> z.getZoneType() == ZoneType.BODY))
                .findFirst();
    }
}
