package com.pptxgenerator.pipeline.assigner;

import com.pptxgenerator.pipeline.assigner.model.LayoutAssignmentWarning;
import com.pptxgenerator.pipeline.assigner.model.SlidePlanWithLayout;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates visual consistency rules on the enriched plan:
 * <ul>
 *   <li>L1: no layout is repeated more than twice consecutively;</li>
 *   <li>L3: the presentation uses more than a single layout when it has several slides.</li>
 * </ul>
 */
@Slf4j
@ApplicationScoped
public class LayoutAssignmentValidator {

    public List<LayoutAssignmentWarning> validate(List<SlidePlanWithLayout> slides) {
        List<LayoutAssignmentWarning> warnings = new ArrayList<>();
        checkTripleRepetition(slides, warnings);
        checkLayoutVariety(slides, warnings);
        return warnings;
    }

    private void checkTripleRepetition(List<SlidePlanWithLayout> slides, List<LayoutAssignmentWarning> warnings) {
        if (slides.size() < 3) {
            return;
        }
        List<Integer> violatingSlides = new ArrayList<>();
        for (int i = 2; i < slides.size(); i++) {
            var layout1 = slides.get(i - 2).getLayout();
            var layout2 = slides.get(i - 1).getLayout();
            var layout3 = slides.get(i).getLayout();
            if (layout1 != null && layout2 != null && layout3 != null
                    && layout1.getLayoutId().equals(layout2.getLayoutId())
                    && layout2.getLayoutId().equals(layout3.getLayoutId())) {
                violatingSlides.add(slides.get(i).getSlideNumber());
            }
        }
        if (!violatingSlides.isEmpty()) {
            warnings.add(LayoutAssignmentWarning.builder()
                    .code("VARIETY_LIMITED")
                    .message("Visual variety is compromised: Triple consecutive layout repetition detected on slides: "
                            + violatingSlides)
                    .affectedSlides(violatingSlides)
                    .build());
        }
    }

    private void checkLayoutVariety(List<SlidePlanWithLayout> slides, List<LayoutAssignmentWarning> warnings) {
        Set<String> usedLayoutIds = slides.stream()
                .filter(s -> s.getLayout() != null)
                .map(s -> s.getLayout().getLayoutId())
                .collect(Collectors.toSet());
        if (usedLayoutIds.size() == 1 && slides.size() > 3) {
            warnings.add(LayoutAssignmentWarning.builder()
                    .code("LAYOUT_LIMITED")
                    .message("Only a single layout was used for the entire presentation. Visual variety is heavily restricted.")
                    .affectedSlides(slides.stream().map(SlidePlanWithLayout::getSlideNumber).toList())
                    .build());
        }
    }
}
