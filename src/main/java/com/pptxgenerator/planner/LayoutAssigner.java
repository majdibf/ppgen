package com.pptxgenerator.planner;

import com.pptxgenerator.client.GenerativeAiGateway;
import com.pptxgenerator.client.builder.GenerativeAiRequestBuilder;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import com.pptxgenerator.model.EnrichedPlan;
import com.pptxgenerator.model.EnrichedSlide;
import com.pptxgenerator.model.PresentationPlan;
import com.pptxgenerator.model.SlideLayout;
import com.pptxgenerator.model.TemplateStructure;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ContentCapacity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@ApplicationScoped
public class LayoutAssigner {

    private static final int MAX_AI_ATTEMPTS = 2;
    private static final long AI_TIMEOUT_SECONDS = 30;

    @Inject
    public GenerativeAiGateway aiGateway;

    @Inject
    public AiResponseParser responseParser;

    public EnrichedPlan assignLayouts(TemplateStructure template, PresentationPlan plan) {
        EnrichedPlan enrichedPlan = new EnrichedPlan(plan);
        List<SlideLayout> catalog = template.getLayouts() == null ? List.of() : template.getLayouts();
        calculateCapacities(catalog);
        List<String> recentLayoutIds = new ArrayList<>(2);

        for (EnrichedSlide slide : enrichedPlan.getSlides()) {
            Assignment assignment = assignSlide(catalog, slide, recentLayoutIds);
            slide.setAssignedLayout(assignment.layout());
            slide.setAssignmentMethod(assignment.method());
            slide.setRationale(assignment.rationale());
            slide.setDynamic(assignment.layout() == null);
            slide.setLayoutMatchScore(assignment.layout() == null ? 0 : 1);

            if (assignment.layout() != null) {
                recentLayoutIds.add(assignment.layout().getLayoutId());
                if (recentLayoutIds.size() > 2) recentLayoutIds.remove(0);
            }
        }

        enrichedPlan.updateDynamicCount();
        enrichedPlan.setTemplateUsed(template.getMetadata() == null
            ? null : template.getMetadata().getTemplateOriginalName());
        return enrichedPlan;
    }

    private Assignment assignSlide(List<SlideLayout> catalog, EnrichedSlide slide,
                                   List<String> recent) {
        String type = slide.getSlideType() != null ? slide.getSlideType() : slide.getType();
        if ("title".equalsIgnoreCase(type) || "cover".equalsIgnoreCase(type)) {
            SlideLayout layout = firstNonRepeated(catalog, "TITLE_SLIDE", recent);
            if (layout == null) layout = firstNonRepeated(catalog, recent);
            if (layout == null) layout = first(catalog);
            return new Assignment(layout, "deterministic", "Règle déterministe: TITLE_SLIDE");
        }
        if ("section_transition".equalsIgnoreCase(type) || "section".equalsIgnoreCase(type)) {
            SlideLayout layout = firstNonRepeated(catalog, "SECTION_HEADER", recent);
            if (layout == null) layout = firstNonRepeated(catalog, "TITLE_SLIDE", recent);
            if (layout == null) layout = firstNonRepeated(catalog, recent);
            if (layout == null) layout = first(catalog);
            return new Assignment(layout, "deterministic", "Règle déterministe: SECTION_HEADER");
        }
        if ("outline".equalsIgnoreCase(type)) {
            SlideLayout layout = largestCapacityNonRepeated(catalog, recent);
            if (layout == null) layout = firstNonRepeated(catalog, recent);
            if (layout == null) layout = first(catalog);
            return new Assignment(layout, "deterministic", "Règle déterministe: plus grande capacité");
        }
        if ("content".equalsIgnoreCase(type)) {
            if (isComparison(slide.getPurpose(), slide.getContentBrief())) {
                SlideLayout layout = firstNonRepeated(catalog, "TWO_COLUMN", recent);
                if (layout != null) {
                    return new Assignment(layout, "deterministic", "Règle mot-clé: comparaison → TWO_COLUMN");
                }
            }
            Assignment aiAssignment = callAI(catalog, slide, recent);
            if (aiAssignment != null && !contains(recent, aiAssignment.layout().getLayoutId())) {
                return aiAssignment;
            }
            SlideLayout fallback = smartFallback(catalog, recent);
            return new Assignment(fallback, "fallback", "Fallback smart");
        }
        SlideLayout layout = firstNonRepeated(catalog, recent);
        if (layout == null) layout = first(catalog);
        return new Assignment(layout, "fallback", "Fallback: premier layout");
    }

    private Assignment callAI(List<SlideLayout> catalog, EnrichedSlide slide, List<String> recent) {
        if (aiGateway == null || catalog.isEmpty()) return null;
        String prompt = buildPrompt(catalog, slide, recent);
        for (int attempt = 1; attempt <= MAX_AI_ATTEMPTS; attempt++) {
            try {
                TextRequestDto request = GenerativeAiRequestBuilder.builder()
                    .systemPrompt("Tu es un expert en design de présentations PowerPoint. Réponds uniquement avec un JSON valide.")
                    .userPrompt(prompt)
                    .temperature(0.2)
                    .maxTokens(500)
                    .build().toRequest();
                CompletableFuture<TextResponseDto> future = CompletableFuture.supplyAsync(
                    () -> aiGateway.processRequest(request));
                TextResponseDto response = future.get(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                AIResponse parsed = responseParser.parseAs(
                    response.getCandidates().get(0).getText(), AIResponse.class);
                SlideLayout selected = catalog.stream()
                    .filter(l -> Objects.equals(l.getLayoutId(), parsed.getLayoutId()))
                    .findFirst().orElse(null);
                if (selected != null) {
                    return new Assignment(selected, "ai", parsed.getRationale());
                }
            } catch (Exception e) {
                log.warn("Layout AI attempt {}/{} failed: {}", attempt, MAX_AI_ATTEMPTS, e.getMessage());
            }
        }
        return null;
    }

    private String buildPrompt(List<SlideLayout> catalog, EnrichedSlide slide, List<String> recent) {
        String layouts = catalog.stream()
            .map(l -> String.format("- %s | type=%s | capacity=%s | zones=%s | %s",
                l.getLayoutId(), l.getSemanticType(), l.getContentCapacity(),
                l.getZones() == null ? List.of() : l.getZones().stream().map(Zone::getZoneType).toList(),
                l.getDescription()))
            .reduce((a, b) -> a + "\n" + b).orElse("Aucun layout");
        return String.format("""
            Purpose: %s
            Content brief: %s
            Layouts disponibles:
            %s
            Les 2 derniers layouts utilisés: %s
            Évite les layouts récents. Comparaison → TWO_COLUMN. Contenu dense → grande capacité.
            Retourne uniquement {"layout_id":"layout_x","rationale":"..."}.
            """, slide.getPurpose(), slide.getContentBrief(), layouts,
            recent.isEmpty() ? "Aucun" : String.join(", ", recent));
    }

    private SlideLayout smartFallback(List<SlideLayout> catalog, List<String> recent) {
        SlideLayout layout = firstNonRepeated(catalog, "CONTENT", recent);
        if (layout == null) layout = firstNonRepeated(catalog, recent);
        return layout == null ? first(catalog) : layout;
    }

    private SlideLayout firstNonRepeated(List<SlideLayout> catalog, String semanticType,
                                         List<String> recent) {
        return catalog.stream()
            .filter(l -> semanticType.equals(l.getSemanticType()))
            .filter(l -> !contains(recent, l.getLayoutId()))
            .findFirst().orElse(null);
    }

    private SlideLayout firstNonRepeated(List<SlideLayout> catalog, List<String> recent) {
        return catalog.stream().filter(l -> !contains(recent, l.getLayoutId())).findFirst().orElse(null);
    }

    private SlideLayout largestCapacityNonRepeated(List<SlideLayout> catalog, List<String> recent) {
        return catalog.stream().filter(l -> "CONTENT".equals(l.getSemanticType()))
            .filter(l -> !contains(recent, l.getLayoutId()))
            .max(Comparator.comparingInt(this::capacityRank)).orElse(null);
    }

    private boolean contains(List<String> recent, String layoutId) {
        return layoutId != null && recent.contains(layoutId);
    }

    private SlideLayout first(List<SlideLayout> catalog) {
        return catalog.isEmpty() ? null : catalog.get(0);
    }

    private boolean isComparison(String purpose, String brief) {
        String text = ((purpose == null ? "" : purpose) + " " + (brief == null ? "" : brief))
            .toLowerCase(Locale.ROOT);
        return text.contains("compar") || text.contains("avant/après") || text.contains("avant après")
            || text.contains("pour/contre") || text.contains("before/after") || text.contains("versus");
    }

    private int capacityRank(SlideLayout layout) {
        return layout.getContentCapacity() == ContentCapacity.HIGH ? 3
            : layout.getContentCapacity() == ContentCapacity.MEDIUM ? 2 : 1;
    }

    private void calculateCapacities(List<SlideLayout> catalog) {
        for (SlideLayout layout : catalog) {
            if (layout.getContentCapacity() != null || layout.getZones() == null) continue;
            long bodyCount = layout.getZones().stream().filter(z -> "body".equals(z.getZoneType())).count();
            double area = layout.getZones().stream().filter(z -> "body".equals(z.getZoneType()))
                .mapToDouble(Zone::getSurfacePercentage).sum();
            layout.setContentCapacity(bodyCount >= 2 || area > 40 ? ContentCapacity.HIGH
                : area > 20 ? ContentCapacity.MEDIUM : ContentCapacity.LOW);
        }
    }

    private record Assignment(SlideLayout layout, String method, String rationale) {}
}
