package com.pptxgenerator.assigner;

import com.pptxgenerator.assigner.ai.AILayoutAssigner;
import com.pptxgenerator.assigner.fallback.FallbackStrategy;
import com.pptxgenerator.assigner.model.AssignmentMethod;
import com.pptxgenerator.assigner.model.ClassifiedLayout;
import com.pptxgenerator.assigner.model.LayoutAssignmentWarning;
import com.pptxgenerator.assigner.model.PlanWithLayouts;
import com.pptxgenerator.assigner.model.SlidePlanWithLayout;
import com.pptxgenerator.assigner.rule.CapacityMatcher;
import com.pptxgenerator.assigner.rule.ComparisonDetector;
import com.pptxgenerator.assigner.rule.DeterministicRuleEngine;
import com.pptxgenerator.assigner.rule.VarietyEnforcer;
import com.pptxgenerator.assigner.validation.LayoutAssignmentValidator;
import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.TemplateAnalysis;
import com.pptxgenerator.planner.model.PresentationPlan;
import com.pptxgenerator.planner.model.SlidePlan;
import com.pptxgenerator.planner.model.SlideType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class LayoutAssignmentService {

    private final DeterministicRuleEngine deterministicEngine;
    private final ComparisonDetector comparisonDetector;
    private final CapacityMatcher capacityMatcher;
    private final VarietyEnforcer varietyEnforcer;
    private final AILayoutAssigner aiAssigner;
    private final FallbackStrategy fallbackStrategy;
    private final LayoutAssignmentValidator validator;
    private final LayoutClassifier layoutClassifier;

    /**
     * Attribue les layouts à chaque slide du plan.
     */
    public PlanWithLayouts assignLayouts(PresentationPlan plan, TemplateAnalysis templateAnalysis) {
        log.info("Step 2: Attribution des layouts pour {} slides", plan.getTotalSlides());

        List<LayoutAnalysis> rawLayouts = templateAnalysis.getLayouts();
        List<ClassifiedLayout> availableLayouts = layoutClassifier.classify(rawLayouts);
        log.info("  Layouts disponibles: {}", availableLayouts.size());

        List<SlidePlanWithLayout> enrichedSlides = new ArrayList<>();
        List<LayoutAssignmentWarning> warnings = new ArrayList<>();

        for (SlidePlan slide : plan.getSlides()) {
            SlidePlanWithLayout enriched = assignLayoutToSlide(slide, availableLayouts, enrichedSlides, warnings);
            enrichedSlides.add(enriched);
        }

        // Validation post-attribution
        List<LayoutAssignmentWarning> validationWarnings = validator.validate(enrichedSlides);
        warnings.addAll(validationWarnings);

        PlanWithLayouts result = PlanWithLayouts.builder()
            .planWithLayouts(PlanWithLayouts.PlanWithLayoutsData.builder()
                .title(plan.getTitle())
                .narrativeArc(plan.getNarrativeArc())
                .totalSlides(plan.getTotalSlides())
                .slides(enrichedSlides)
                .build())
            .warnings(warnings)
            .build();

        log.info("Step 2 terminé: {} slides enrichies, {} warnings", enrichedSlides.size(), warnings.size());
        return result;
    }

    private SlidePlanWithLayout assignLayoutToSlide(SlidePlan slide,
                                                     List<ClassifiedLayout> availableLayouts,
                                                     List<SlidePlanWithLayout> previousSlides,
                                                     List<LayoutAssignmentWarning> warnings) {

        ClassifiedLayout chosenLayout;
        String rationale;
        AssignmentMethod method;
        String warningCode = null;

        // 1. Règles déterministes (L4)
        Optional<DeterministicRuleEngine.AssignmentResult> deterministic =
            deterministicEngine.tryAssign(slide.getSlideType(), availableLayouts);

        if (deterministic.isPresent()) {
            var result = deterministic.get();
            chosenLayout = result.layout();
            rationale = result.rationale();
            method = AssignmentMethod.DETERMINISTIC;
            warningCode = result.warningCode();
        }
        // 2. Détection de comparaison (L3)
        else if (slide.getSlideType() == SlideType.CONTENT) {
            Optional<ClassifiedLayout> twoColumn = comparisonDetector.tryAssignTwoColumn(
                slide.getPurpose(), slide.getContentBrief(), availableLayouts
            );

            if (twoColumn.isPresent()) {
                chosenLayout = twoColumn.get();
                rationale = "Purpose mentionne une comparaison → TWO_COLUMN (règle L3)";
                method = AssignmentMethod.DETERMINISTIC;
            }
            // 3. Filtrage par densité (L2)
            else {
                List<ClassifiedLayout> usableForContent = fallbackStrategy.filterUsableForContent(availableLayouts);
                CapacityMatcher.EstimatedDensity density = capacityMatcher.estimateDensity(slide.getContentBrief());
                List<ClassifiedLayout> densityFiltered = capacityMatcher.filterByDensity(usableForContent, density);

                // 4. Exclusion pour variété (L1)
                List<ClassifiedLayout> varietyFiltered = varietyEnforcer.excludeViolatingLayouts(
                    densityFiltered, previousSlides
                );

                // 5. Appel IA si nécessaire
                List<ClassifiedLayout> layoutsForAI = varietyFiltered.isEmpty() ? usableForContent : varietyFiltered;

                Optional<AILayoutAssigner.AssignmentResult> aiResult = aiAssigner.assign(
                    slide.getPurpose(), slide.getContentBrief(), layoutsForAI, previousSlides
                );

                if (aiResult.isPresent()) {
                    var result = aiResult.get();
                    chosenLayout = result.layout();
                    rationale = result.rationale();
                    method = AssignmentMethod.AI_ASSISTED;
                    warningCode = result.warningCode();
                }
                // 6. Fallback ultime (L5)
                else {
                    Optional<ClassifiedLayout> fallback = fallbackStrategy.findUltimateFallback(
                        availableLayouts, slide.getSlideType()
                    );
                    chosenLayout = fallback.orElse(availableLayouts.get(0));
                    rationale = "Fallback ultime: " + chosenLayout.getSemanticType();
                    method = AssignmentMethod.FALLBACK;
                    warningCode = "LAYOUT_FALLBACK";
                }
            }
        }
        // 7. Fallback pour types inconnus
        else {
            chosenLayout = availableLayouts.get(0);
            rationale = "Type de slide inconnu '" + slide.getSlideType() + "', utilisation du premier layout";
            method = AssignmentMethod.FALLBACK;
            warningCode = "LAYOUT_FALLBACK";
        }

        // Vérification finale de la variété (L1)
        if (!varietyEnforcer.respectsVariety(chosenLayout, previousSlides)) {
            log.warn("Règle L1 violée pour slide {}: layout {} répété 3 fois",
                slide.getSlideNumber(), chosenLayout.getSemanticType());
            // On ne bloque pas, on log juste un warning
        }

        // Enregistrer un warning si nécessaire
        if (warningCode != null) {
            warnings.add(LayoutAssignmentWarning.builder()
                .code(warningCode)
                .message(rationale)
                .affectedSlides(List.of(slide.getSlideNumber()))
                .build());
        }

        return SlidePlanWithLayout.builder()
            .slideNumber(slide.getSlideNumber())
            .slideType(slide.getSlideType())
            .purpose(slide.getPurpose())
            .contentBrief(slide.getContentBrief())
            .detailedContext(slide.getDetailedContext())
            .layout(ClassifiedLayout.builder()
                .layoutId(chosenLayout.getLayoutId())
                .originalName(chosenLayout.getOriginalName())
                .semanticType(chosenLayout.getSemanticType())
                .description(chosenLayout.getDescription())
                .contentCapacity(chosenLayout.getContentCapacity())
                .zones(chosenLayout.getZones())
                .build())
            .build();
    }
}
