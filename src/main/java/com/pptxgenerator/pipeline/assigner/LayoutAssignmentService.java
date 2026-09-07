package com.pptxgenerator.pipeline.assigner;

import com.pptxgenerator.pipeline.assigner.model.ClassifiedLayout;
import com.pptxgenerator.pipeline.assigner.model.LayoutAssignmentResult;
import com.pptxgenerator.pipeline.assigner.model.LayoutAssignmentWarning;
import com.pptxgenerator.pipeline.assigner.model.PlanWithLayouts;
import com.pptxgenerator.pipeline.assigner.model.SlidePlanWithLayout;
import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.TemplateAnalysis;
import com.pptxgenerator.pipeline.planner.model.PresentationPlan;
import com.pptxgenerator.pipeline.planner.model.SlidePlan;
import com.pptxgenerator.pipeline.planner.model.SlideType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates layout assignment (Step 3): deterministic assignment for title/section/outline
 * slides, AI-assisted assignment for content slides, and a safety fallback. Produces an enriched
 * plan of {@link SlidePlanWithLayout} plus variety warnings.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class LayoutAssignmentService {

    private final DeterministicLayoutAssigner deterministicLayoutAssigner;
    private final AILayoutAssigner aiAssigner;
    private final FallbackAssignment fallbackAssignment;
    private final LayoutAssignmentValidator validator;

    public PlanWithLayouts assignLayouts(PresentationPlan plan, TemplateAnalysis templateAnalysis) {
        log.info("Step 3: Assigning layouts for {} slides", plan.getTotalSlides());

        List<LayoutAnalysis> availableLayouts = templateAnalysis.getLayouts();
        if (availableLayouts == null || availableLayouts.isEmpty()) {
            throw new IllegalStateException("Template has no layouts available for assignment");
        }
        log.info("Available layouts: {}", availableLayouts.size());

        List<SlidePlanWithLayout> enrichedSlides = new ArrayList<>();
        List<LayoutAssignmentWarning> warnings = new ArrayList<>();

        for (SlidePlan slide : plan.getSlides()) {
            SlidePlanWithLayout enriched = assignLayoutToSlide(slide, availableLayouts, enrichedSlides, warnings);
            enrichedSlides.add(enriched);
        }

        warnings.addAll(validator.validate(enrichedSlides));

        log.info("Step 3 completed: {} enriched slides, {} warnings", enrichedSlides.size(), warnings.size());

        return PlanWithLayouts.builder()
                .title(plan.getTitle())
                .narrativeArc(plan.getNarrativeArc())
                .totalSlides(plan.getTotalSlides())
                .slides(enrichedSlides)
                .warnings(warnings)
                .build();
    }

    private SlidePlanWithLayout assignLayoutToSlide(SlidePlan slide,
                                                   List<LayoutAnalysis> availableLayouts,
                                                   List<SlidePlanWithLayout> previousSlides,
                                                   List<LayoutAssignmentWarning> warnings) {
        LayoutAssignmentResult result = determineLayout(slide, availableLayouts, previousSlides);

        if (result.warningCode() != null) {
            warnings.add(LayoutAssignmentWarning.builder()
                    .code(result.warningCode())
                    .message(result.rationale())
                    .affectedSlides(List.of(slide.getSlideNumber()))
                    .build());
        }

        return SlidePlanWithLayout.builder()
                .slideNumber(slide.getSlideNumber())
                .slideType(slide.getSlideType())
                .purpose(slide.getPurpose())
                .contentBrief(slide.getContentBrief())
                .detailedContext(slide.getDetailedContext())
                .sectionNumber(slide.getSectionNumber())
                .sectionTitle(slide.getSectionTitle())
                .layout(toClassifiedLayout(result.layout()))
                .build();
    }

    private LayoutAssignmentResult determineLayout(SlidePlan slide,
                                                   List<LayoutAnalysis> availableLayouts,
                                                   List<SlidePlanWithLayout> previousSlides) {
        Optional<LayoutAssignmentResult> deterministic =
                deterministicLayoutAssigner.assign(slide.getSlideType(), availableLayouts);
        if (deterministic.isPresent()) {
            return deterministic.get();
        }

        if (slide.getSlideType() == SlideType.CONTENT) {
            List<LayoutAnalysis> usableForContent = fallbackAssignment.filterUsableForContent(availableLayouts);
            Optional<LayoutAssignmentResult> aiResult = aiAssigner.assign(
                    slide.getPurpose(), slide.getContentBrief(), usableForContent, previousSlides);
            if (aiResult.isPresent()) {
                return aiResult.get();
            }
            LayoutAnalysis fallback = fallbackAssignment.findUltimateFallback(availableLayouts, slide.getSlideType())
                    .orElse(availableLayouts.get(0));
            return new LayoutAssignmentResult(fallback,
                    "Fallback (AI error): " + fallback.getSemanticType(), "LAYOUT_FALLBACK");
        }

        LayoutAnalysis baseFallback = availableLayouts.get(0);
        return new LayoutAssignmentResult(baseFallback,
                "Unknown slide type " + slide.getSlideType() + ", using first available layout", "LAYOUT_FALLBACK");
    }

    /** Converts a template {@link LayoutAnalysis} into the plan-time {@link ClassifiedLayout} carrier. */
    private ClassifiedLayout toClassifiedLayout(LayoutAnalysis layout) {
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
