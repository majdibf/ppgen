package com.pptxgenerator.assigner.rule;

import com.pptxgenerator.assigner.model.ClassifiedLayout;
import com.pptxgenerator.model.enums.SemanticType;
import com.pptxgenerator.model.enums.ZoneType;
import com.pptxgenerator.planner.model.SlideType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * Règle L4 : Attribution déterministe pour les cas évidents.
 * - title → TITLE_SLIDE
 * - section_transition → SECTION_HEADER
 * - outline → OUTLINE ou CONTENT (plus haute capacité)
 */
@Slf4j
@ApplicationScoped
public class DeterministicRuleEngine {

    /**
     * Tente d'appliquer une règle déterministe.
     * @return Optional du layout + rationale, ou empty si pas de règle applicable
     */
    public Optional<AssignmentResult> tryAssign(SlideType slideType, List<ClassifiedLayout> layouts) {
        return switch (slideType) {
            case TITLE -> assignTitleLayout(layouts);
            case SECTION_TRANSITION -> assignSectionTransitionLayout(layouts);
            case OUTLINE -> assignOutlineLayout(layouts);
            case CONTENT -> Optional.empty(); // Géré par l'IA
        };
    }

    private Optional<AssignmentResult> assignTitleLayout(List<ClassifiedLayout> layouts) {
        // Priorité 1 : TITLE_SLIDE
        Optional<ClassifiedLayout> titleSlide = findFirst(layouts, SemanticType.TITLE_SLIDE);
        if (titleSlide.isPresent()) {
            return Optional.of(new AssignmentResult(
                titleSlide.get(),
                "Slide de type 'title' → TITLE_SLIDE automatique"
            ));
        }

        // Fallback : SECTION_HEADER
        Optional<ClassifiedLayout> sectionHeader = findFirst(layouts, SemanticType.SECTION_HEADER);
        if (sectionHeader.isPresent()) {
            return Optional.of(new AssignmentResult(
                sectionHeader.get(),
                "Slide de type 'title' → SECTION_HEADER (TITLE_SLIDE indisponible)",
                "LAYOUT_FALLBACK"
            ));
        }

        return Optional.empty();
    }

    private Optional<AssignmentResult> assignSectionTransitionLayout(List<ClassifiedLayout> layouts) {
        Optional<ClassifiedLayout> sectionHeader = findFirst(layouts, SemanticType.SECTION_HEADER);
        if (sectionHeader.isPresent()) {
            return Optional.of(new AssignmentResult(
                sectionHeader.get(),
                "Slide de type 'section_transition' → SECTION_HEADER automatique"
            ));
        }

        Optional<ClassifiedLayout> titleSlide = findFirst(layouts, SemanticType.TITLE_SLIDE);
        if (titleSlide.isPresent()) {
            return Optional.of(new AssignmentResult(
                titleSlide.get(),
                "Slide de type 'section_transition' → TITLE_SLIDE (SECTION_HEADER indisponible)",
                "LAYOUT_FALLBACK"
            ));
        }

        return Optional.empty();
    }

    private Optional<AssignmentResult> assignOutlineLayout(List<ClassifiedLayout> layouts) {
        // Priorité 1 : OUTLINE
        Optional<ClassifiedLayout> outline = findFirst(layouts, SemanticType.OUTLINE);
        if (outline.isPresent()) {
            return Optional.of(new AssignmentResult(
                outline.get(),
                "Slide de type 'outline' → OUTLINE automatique"
            ));
        }

        // Priorité 2 : CONTENT avec la plus grande zone body
        List<ClassifiedLayout> contentLayouts = layouts.stream()
            .filter(l -> l.getSemanticType() == SemanticType.CONTENT)
            .toList();

        if (!contentLayouts.isEmpty()) {
            ClassifiedLayout best = contentLayouts.stream()
                .max((l1, l2) -> Long.compare(getMaxBodySurface(l1), getMaxBodySurface(l2)))
                .orElse(contentLayouts.get(0));

            return Optional.of(new AssignmentResult(
                best,
                "Slide de type 'outline' → CONTENT avec la plus grande zone body (aucun OUTLINE disponible)",
                "LAYOUT_FALLBACK"
            ));
        }

        // Fallback : layout avec zone body
        Optional<ClassifiedLayout> withBody = layouts.stream()
            .filter(l -> l.getZones().stream().anyMatch(z -> z.getZoneType() == ZoneType.BODY))
            .findFirst();

        if (withBody.isPresent()) {
            return Optional.of(new AssignmentResult(
                withBody.get(),
                "Slide de type 'outline' → layout avec zone body",
                "LAYOUT_FALLBACK"
            ));
        }

        return Optional.empty();
    }

    private Optional<ClassifiedLayout> findFirst(List<ClassifiedLayout> layouts, SemanticType type) {
        return layouts.stream()
            .filter(l -> l.getSemanticType() == type)
            .findFirst();
    }

    private long getMaxBodySurface(ClassifiedLayout layout) {
        return layout.getZones().stream()
            .filter(z -> z.getZoneType() == ZoneType.BODY)
            .mapToLong(z -> z.getWidth() * z.getHeight())
            .max()
            .orElse(0L);
    }

    /**
     * Résultat d'une règle déterministe
     */
    public record AssignmentResult(
        ClassifiedLayout layout,
        String rationale,
        String warningCode  // null si pas de warning
    ) {
        public AssignmentResult(ClassifiedLayout layout, String rationale) {
            this(layout, rationale, null);
        }
    }
}
