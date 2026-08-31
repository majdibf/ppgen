package com.pptxgenerator.pipeline.assigner;

import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.enums.SemanticType;
import com.pptxgenerator.model.enums.ZoneType;
import com.pptxgenerator.pipeline.assigner.model.LayoutAssignmentResult;
import com.pptxgenerator.pipeline.planner.model.SlideType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic (rule-based) layout assignment for non-content slides:
 * title, section transitions and outline are resolved without AI, with documented fallbacks.
 * Content slides are delegated to the AI assigner.
 */
@Slf4j
@ApplicationScoped
public class DeterministicLayoutAssigner {

    public Optional<LayoutAssignmentResult> assign(SlideType slideType, List<LayoutAnalysis> layouts) {
        return switch (slideType) {
            case TITLE -> assignTitleLayout(layouts);
            case SECTION_TRANSITION -> assignSectionTransitionLayout(layouts);
            case OUTLINE -> assignOutlineLayout(layouts);
            case CONTENT -> Optional.empty(); // Delegated to AI
        };
    }

    private Optional<LayoutAssignmentResult> assignTitleLayout(List<LayoutAnalysis> layouts) {
        Optional<LayoutAnalysis> titleSlide = findFirst(layouts, SemanticType.TITLE_SLIDE);
        if (titleSlide.isPresent()) {
            return Optional.of(new LayoutAssignmentResult(titleSlide.get(),
                    "Slide type 'TITLE' -> Automatic TITLE_SLIDE assignment", null));
        }
        return findFirst(layouts, SemanticType.SECTION_HEADER)
                .map(layout -> new LayoutAssignmentResult(layout,
                        "Slide type 'TITLE' -> SECTION_HEADER fallback (TITLE_SLIDE not available)",
                        "LAYOUT_FALLBACK"));
    }

    private Optional<LayoutAssignmentResult> assignSectionTransitionLayout(List<LayoutAnalysis> layouts) {
        Optional<LayoutAnalysis> sectionHeader = findFirst(layouts, SemanticType.SECTION_HEADER);
        if (sectionHeader.isPresent()) {
            return Optional.of(new LayoutAssignmentResult(sectionHeader.get(),
                    "Slide type 'SECTION_TRANSITION' -> Automatic SECTION_HEADER assignment", null));
        }
        return findFirst(layouts, SemanticType.TITLE_SLIDE)
                .map(layout -> new LayoutAssignmentResult(layout,
                        "Slide type 'SECTION_TRANSITION' -> TITLE_SLIDE fallback (SECTION_HEADER not available)",
                        "LAYOUT_FALLBACK"));
    }

    private Optional<LayoutAssignmentResult> assignOutlineLayout(List<LayoutAnalysis> layouts) {
        Optional<LayoutAnalysis> outline = findFirst(layouts, SemanticType.OUTLINE);
        if (outline.isPresent()) {
            return Optional.of(new LayoutAssignmentResult(outline.get(),
                    "Slide type 'OUTLINE' -> Automatic OUTLINE assignment", null));
        }
        List<LayoutAnalysis> contentLayouts = layouts.stream()
                .filter(l -> l.getSemanticType() == SemanticType.CONTENT)
                .toList();
        if (!contentLayouts.isEmpty()) {
            LayoutAnalysis best = contentLayouts.stream()
                    .max(Comparator.comparingLong(this::getMaxBodySurface))
                    .orElse(contentLayouts.get(0));
            return Optional.of(new LayoutAssignmentResult(best,
                    "Slide type 'OUTLINE' -> CONTENT candidate with the largest body zone (No OUTLINE available)",
                    "LAYOUT_FALLBACK"));
        }
        return layouts.stream()
                .filter(l -> l.getZones().stream().anyMatch(z -> z.getZoneType() == ZoneType.BODY))
                .findFirst()
                .map(layout -> new LayoutAssignmentResult(layout,
                        "Slide type 'OUTLINE' -> Layout with a matching body zone fallback",
                        "LAYOUT_FALLBACK"));
    }

    private Optional<LayoutAnalysis> findFirst(List<LayoutAnalysis> layouts, SemanticType type) {
        return layouts.stream()
                .filter(l -> l.getSemanticType() == type)
                .findFirst();
    }

    private long getMaxBodySurface(LayoutAnalysis layout) {
        return layout.getZones().stream()
                .filter(z -> z.getZoneType() == ZoneType.BODY)
                .mapToLong(z -> (z.getWidth() != null ? z.getWidth() : 0L) * (z.getHeight() != null ? z.getHeight() : 0L))
                .max()
                .orElse(0L);
    }
}
