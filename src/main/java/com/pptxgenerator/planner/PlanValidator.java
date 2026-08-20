package com.pptxgenerator.planner;

import com.pptxgenerator.common.exception.AIPipelineException;
import com.pptxgenerator.planner.model.PresentationPlan;
import com.pptxgenerator.planner.model.SlidePlan;
import com.pptxgenerator.planner.model.SlideType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Valide le plan généré selon les règles N1-N6 de la spec.
 *
 * N1 : Un message par slide
 * N2 : Progression logique
 * N3 : Rythme (alterner dense/léger)
 * N4 : Slide types explicites
 * N5 : Contexte autosuffisant
 * N6 : Bornes respectées [min, max]
 */
@Slf4j
@ApplicationScoped
public class PlanValidator {

    /**
     * Valide le plan. Corrige ce qui peut l'être, lève une exception pour les erreurs critiques.
     */
    public void validateAndFix(PresentationPlan plan, int minSlides, int maxSlides) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (plan == null) {
            throw new AIPipelineException("Le plan généré est null");
        }

        // N6 : Bornes respectées
        validateBounds(plan, minSlides, maxSlides, errors);

        // N4 : Slide types explicites
        validateSlideTypes(plan, errors);

        // N1 : Un message par slide (purpose non vide)
        validateOneMessagePerSlide(plan, errors);

        // N5 : Contexte autosuffisant
        validateContextAutosufficiency(plan, warnings);

        // N3 : Rythme (pas plus de 3 slides content consécutives)
        validateRhythm(plan, warnings);

        // Structure : première slide = title
        validateFirstSlideIsTitle(plan, errors);

        // Cohérence : total_slides == slides.size()
        fixTotalSlidesCount(plan);

        // Log warnings
        if (!warnings.isEmpty()) {
            warnings.forEach(w -> log.warn("[PlanValidator] {}", w));
        }

        // Erreurs critiques
        if (!errors.isEmpty()) {
            log.error("[PlanValidator] Erreurs critiques:");
            errors.forEach(e -> log.error("  - {}", e));
            throw new AIPipelineException("Plan invalide:\n" + String.join("\n", errors));
        }

        log.info("[PlanValidator] Validation réussie: {} slides", plan.getSlides().size());
    }

    /**
     * N6 : Le nombre de slides doit être dans [min, max]
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
     * N4 : Chaque slide doit avoir un type valide
     */
    private void validateSlideTypes(PresentationPlan plan, List<String> errors) {
        if (plan.getSlides() == null) {
            errors.add("N4: La liste de slides est null");
            return;
        }

        for (SlidePlan slide : plan.getSlides()) {
            if (slide.getSlideType() == null) {
                errors.add(String.format("N4: Slide %d n'a pas de slide_type", slide.getSlideNumber()));
            }
        }
    }

    /**
     * N1 : Chaque slide doit avoir un purpose non vide
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
     * N5 : Le detailed_context doit être rempli (warning si vide)
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
     * N3 : Pas plus de 3 slides "content" consécutives
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
                    break;
                }
            } else {
                consecutiveContent = 0;
            }
        }
    }

    /**
     * La première slide doit être de type "title"
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
     * Corrige total_slides pour qu'il corresponde à la taille réelle
     */
    private void fixTotalSlidesCount(PresentationPlan plan) {
        if (plan.getSlides() != null) {
            plan.setTotalSlides(plan.getSlides().size());
        }
    }
}
