package com.pptxgenerator.assigner.validation;

import com.pptxgenerator.assigner.model.LayoutAssignmentWarning;
import com.pptxgenerator.assigner.model.SlidePlanWithLayout;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validation post-attribution des layouts.
 */
@Slf4j
@ApplicationScoped
public class LayoutAssignmentValidator {

    /**
     * Vérifie les règles L1-L5 sur le plan enrichi.
     */
    public List<LayoutAssignmentWarning> validate(List<SlidePlanWithLayout> slides) {
        List<LayoutAssignmentWarning> warnings = new ArrayList<>();

        checkTripleRepetition(slides, warnings);
        checkLayoutVariety(slides, warnings);

        return warnings;
    }

    /**
     * Vérifie qu'aucun type de layout n'apparaît plus de 2 fois consécutivement.
     */
    private void checkTripleRepetition(List<SlidePlanWithLayout> slides, List<LayoutAssignmentWarning> warnings) {
        if (slides.size() < 3) return;

        List<Integer> violatingSlides = new ArrayList<>();

        for (int i = 2; i < slides.size(); i++) {
            var s1 = slides.get(i - 2).getLayout();
            var s2 = slides.get(i - 1).getLayout();
            var s3 = slides.get(i).getLayout();

            if (s1 != null && s2 != null && s3 != null &&
                s1.getSemanticType() == s2.getSemanticType() &&
                s2.getSemanticType() == s3.getSemanticType()) {
                violatingSlides.add(slides.get(i).getSlideNumber());
            }
        }

        if (!violatingSlides.isEmpty()) {
            warnings.add(LayoutAssignmentWarning.builder()
                .code("VARIETY_LIMITED")
                .message(String.format(
                    "Triple répétition détectée pour les slides %s. La variété visuelle peut être affectée.",
                    violatingSlides))
                .affectedSlides(violatingSlides)
                .build());
        }
    }

    /**
     * Vérifie la variété globale des layouts utilisés.
     */
    private void checkLayoutVariety(List<SlidePlanWithLayout> slides, List<LayoutAssignmentWarning> warnings) {
        Set<String> usedLayoutIds = new HashSet<>();
        for (var slide : slides) {
            if (slide.getLayout() != null) {
                usedLayoutIds.add(slide.getLayout().getLayoutId());
            }
        }

        if (usedLayoutIds.size() == 1 && slides.size() > 3) {
            warnings.add(LayoutAssignmentWarning.builder()
                .code("LAYOUT_LIMITED")
                .message("Un seul layout utilisé pour toute la présentation. La variété visuelle est limitée.")
                .affectedSlides(slides.stream().map(SlidePlanWithLayout::getSlideNumber).toList())
                .build());
        }
    }
}
