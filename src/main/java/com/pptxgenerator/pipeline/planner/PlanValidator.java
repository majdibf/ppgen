package com.pptxgenerator.pipeline.planner;

import com.pptxgenerator.common.exception.AIPipelineException;
import com.pptxgenerator.pipeline.planner.model.PresentationPlan;
import com.pptxgenerator.pipeline.planner.model.SlidePlan;
import com.pptxgenerator.pipeline.planner.model.SlideType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates the generated plan according to the N1-N6 rules of the spec.
 *
 * N1 : One message per slide
 * N2 : Logical progression
 * N3 : Rhythm (alternate dense/light slides)
 * N4 : Explicit slide types
 * N5 : Self-sufficient context
 * N6 : Bounds respected [min, max]
 */
@Slf4j
@ApplicationScoped
public class PlanValidator {

    /**
     * Validates the plan. Fixes what can be fixed and throws an exception for critical errors.
     */
    public void validateAndFix(PresentationPlan plan, int minSlides, int maxSlides) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (plan == null) {
            throw new AIPipelineException("Le plan généré est null");
        }

        // N6 : Bounds respected
        validateBounds(plan, minSlides, maxSlides, errors);

        // N4 : Explicit slide types (automatic correction)
        validateSlideTypes(plan, errors, warnings);

        // N1 : One message per slide (non-empty purpose)
        validateOneMessagePerSlide(plan, errors);

        // N5 : Self-sufficient context
        validateContextAutosufficiency(plan, warnings);

        // N3 : Rhythm (no more than 3 consecutive content slides)
        validateRhythm(plan, warnings);

        // Structure : first slide must be a title
        validateFirstSlideIsTitle(plan, errors);

        // Coherence : total_slides == slides.size()
        fixTotalSlidesCount(plan);

        // Log warnings
        if (!warnings.isEmpty()) {
            warnings.forEach(w -> log.warn("[PlanValidator] {}", w));
        }

        // Critical errors
        if (!errors.isEmpty()) {
            log.error("[PlanValidator] Erreurs critiques:");
            errors.forEach(e -> log.error("  - {}", e));
            throw new AIPipelineException("Plan invalide:\n" + String.join("\n", errors));
        }

        log.info("[PlanValidator] Validation réussie: {} slides", plan.getSlides().size());
    }

    /**
     * N6 : The number of slides must be within [min, max]
     */
    private void validateBounds(PresentationPlan plan, int minSlides, int maxSlides, List<String> errors) {
        int count = plan.getSlides() != null ? plan.getSlides().size() : 0;

        if (count < minSlides) {
            errors.add(String.format(
                "N6: Nombre de slides (%d) inférieur au minimum (%d)", count, minSlides));
        }

        if (count > maxSlides) {
            log.warn("[PlanValidator] N6: {} slides générées, tronquage à {}", count, maxSlides);
            plan.setSlides(plan.getSlides().subList(0, maxSlides));
        }
    }

    /**
     * N4 : Each slide must have a valid type. Fixes null types to CONTENT.
     */
    private void validateSlideTypes(PresentationPlan plan, List<String> errors, List<String> warnings) {
        if (plan.getSlides() == null) {
            errors.add("N4: La liste de slides est null");
            return;
        }

        for (SlidePlan slide : plan.getSlides()) {
            if (slide.getSlideType() == null) {
                slide.setSlideType(SlideType.CONTENT);
                warnings.add(String.format(
                    "N4: Slide %d n'avait pas de slide_type, corrigé en CONTENT", slide.getSlideNumber()));
            }
        }
    }

    /**
     * N1 : Each slide must have a non-empty purpose
     */
    private void validateOneMessagePerSlide(PresentationPlan plan, List<String> errors) {
        for (SlidePlan slide : plan.getSlides()) {
            if (slide.getPurpose() == null || slide.getPurpose().isBlank()) {
                errors.add(String.format("N1: Slide %d n'a pas de purpose", slide.getSlideNumber()));
            }
            if (slide.getContentBrief() == null || slide.getContentBrief().isBlank()) {
                errors.add(String.format("N1: Slide %d n'a pas de content_brief", slide.getSlideNumber()));
            }
        }
    }

    /**
     * N5 : The detailed_context must be filled in (warning if empty)
     */
    private void validateContextAutosufficiency(PresentationPlan plan, List<String> warnings) {
        for (SlidePlan slide : plan.getSlides()) {
            if (slide.getSlideType() == SlideType.CONTENT
                && (slide.getDetailedContext() == null || slide.getDetailedContext().isBlank())) {
                warnings.add(String.format(
                    "N5: Slide %d (content) n'a pas de detailed_context", slide.getSlideNumber()));
            }
        }
    }

    /**
     * N3 : No more than 3 consecutive "content" slides
     */
    private void validateRhythm(PresentationPlan plan, List<String> warnings) {
        int consecutiveContent = 0;

        for (SlidePlan slide : plan.getSlides()) {
            if (slide.getSlideType() == SlideType.CONTENT) {
                consecutiveContent++;
                if (consecutiveContent > 3) {
                    warnings.add(String.format(
                        "N3: %d slides 'content' consécutives détectées (à partir de la slide %d). " +
                        "Envisager d'insérer une transition.",
                        consecutiveContent, slide.getSlideNumber() - consecutiveContent + 1));
                }
            } else {
                consecutiveContent = 0;
            }
        }
    }

    /**
     * The first slide must be of type "title"
     */
    private void validateFirstSlideIsTitle(PresentationPlan plan, List<String> errors) {
        if (plan.getSlides() == null || plan.getSlides().isEmpty()) {
            errors.add("Le plan ne contient aucune slide");
            return;
        }

        SlidePlan first = plan.getSlides().get(0);
        if (first.getSlideType() != SlideType.TITLE) {
            errors.add("La première slide doit être de type 'title', trouvé: " + first.getSlideType());
        }
    }

    /**
     * Fixes total_slides so it matches the actual size
     */
    private void fixTotalSlidesCount(PresentationPlan plan) {
        if (plan.getSlides() != null) {
            plan.setTotalSlides(plan.getSlides().size());
        }
    }
}
