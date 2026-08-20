package com.pptxgenerator.assigner.fallback;

import com.pptxgenerator.assigner.model.ClassifiedLayout;
import com.pptxgenerator.model.enums.SemanticType;
import com.pptxgenerator.model.enums.ZoneType;
import com.pptxgenerator.planner.model.SlideType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * Règle L5 : Fallback gracieux pour les templates pauvres.
 */
@Slf4j
@ApplicationScoped
public class FallbackStrategy {

    /**
     * Fallback ultime : trouve le meilleur layout disponible.
     */
    public Optional<ClassifiedLayout> findUltimateFallback(List<ClassifiedLayout> allLayouts, SlideType slideType) {
        if (allLayouts.isEmpty()) {
            return Optional.empty();
        }

        // Priorité selon le type de slide
        return switch (slideType) {
            case TITLE -> findFirstByType(allLayouts, SemanticType.TITLE_SLIDE)
                .or(() -> findFirstByType(allLayouts, SemanticType.SECTION_HEADER))
                .or(() -> Optional.of(allLayouts.get(0)));

            case SECTION_TRANSITION -> findFirstByType(allLayouts, SemanticType.SECTION_HEADER)
                .or(() -> findFirstByType(allLayouts, SemanticType.TITLE_SLIDE))
                .or(() -> Optional.of(allLayouts.get(0)));

            case OUTLINE -> findFirstByType(allLayouts, SemanticType.OUTLINE)
                .or(() -> findFirstByType(allLayouts, SemanticType.CONTENT))
                .or(() -> findLayoutWithBody(allLayouts))
                .or(() -> Optional.of(allLayouts.get(0)));

            case CONTENT -> findFirstByType(allLayouts, SemanticType.CONTENT)
                .or(() -> findFirstByType(allLayouts, SemanticType.TWO_COLUMN))
                .or(() -> findFirstByType(allLayouts, SemanticType.CONTENT_WITH_MEDIA))
                .or(() -> findLayoutWithBody(allLayouts))
                .or(() -> Optional.of(allLayouts.get(0)));
        };
    }

    /**
     * Filtre les layouts utilisables pour une slide content.
     * Exclut : OUTLINE, TITLE_SLIDE, SECTION_HEADER, CUSTOM, BLANK
     */
    public List<ClassifiedLayout> filterUsableForContent(List<ClassifiedLayout> layouts) {
        return layouts.stream()
            .filter(l -> !List.of(
                SemanticType.OUTLINE,
                SemanticType.TITLE_SLIDE,
                SemanticType.SECTION_HEADER,
                SemanticType.CUSTOM,
                SemanticType.BLANK
            ).contains(l.getSemanticType()))
            .toList();
    }

    private Optional<ClassifiedLayout> findFirstByType(List<ClassifiedLayout> layouts, SemanticType type) {
        return layouts.stream()
            .filter(l -> l.getSemanticType() == type)
            .findFirst();
    }

    private Optional<ClassifiedLayout> findLayoutWithBody(List<ClassifiedLayout> layouts) {
        return layouts.stream()
            .filter(l -> l.getZones() != null &&
                        l.getZones().stream().anyMatch(z -> z.getZoneType() == ZoneType.BODY))
            .findFirst();
    }
}
