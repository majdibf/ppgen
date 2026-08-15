package com.pptxgenerator.planner;

import com.pptxgenerator.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@ApplicationScoped
public class LayoutAssigner {

    public EnrichedPlan assignLayouts(TemplateStructure template, PresentationPlan plan) {
        log.info("Assigning layouts for plan: {}", plan.getTitle());
        
        EnrichedPlan enrichedPlan = new EnrichedPlan(plan);
        List<SlideLayout> availableLayouts = template.getLayouts();
        
        if (availableLayouts == null || availableLayouts.isEmpty()) {
            log.warn("No layouts available in template, all slides will be marked as dynamic");
            markAllAsDynamic(enrichedPlan);
            return enrichedPlan;
        }
        
        Set<String> usedLayoutIds = new HashSet<>();
        
        for (EnrichedSlide slide : enrichedPlan.getSlides()) {
            SlideLayout bestLayout = findBestLayout(slide, availableLayouts, usedLayoutIds);
            
            if (bestLayout != null) {
                slide.setAssignedLayout(bestLayout);
                slide.setDynamic(false);
                slide.setLayoutMatchScore(calculateMatchScore(slide, bestLayout));
                usedLayoutIds.add(bestLayout.getLayoutId());
                log.debug("Assigned layout '{}' to slide '{}' (score: {})", 
                    bestLayout.getLayoutId(), slide.getTitle(), String.format("%.2f", slide.getLayoutMatchScore()));
            } else {
                slide.setDynamic(true);
                slide.setLayoutMatchScore(0.0);
                log.debug("No suitable layout found for slide '{}', marked as dynamic", slide.getTitle());
            }
        }
        
        enrichedPlan.updateDynamicCount();
        enrichedPlan.setTemplateUsed(template.getMetadata().getTemplateOriginalName());
        
        log.info("Layout assignment complete: {} slides, {} dynamic", 
            enrichedPlan.getSlideCount(), enrichedPlan.getDynamicSlideCount());
        
        return enrichedPlan;
    }
    
    private SlideLayout findBestLayout(EnrichedSlide slide, List<SlideLayout> layouts, Set<String> usedLayoutIds) {
        String targetType = mapSlideTypeToSemanticType(slide.getType());
        
        List<SlideLayout> candidates = layouts.stream()
                .filter(layout -> targetType.equals(layout.getSemanticType()))
                .toList();
        
        if (candidates.isEmpty()) {
            candidates = layouts.stream()
                    .filter(layout -> "CONTENT".equals(layout.getSemanticType()))
                    .toList();
        }
        
        if (candidates.isEmpty()) {
            return null;
        }
        
        SlideLayout bestLayout = null;
        double bestScore = -1;
        
        for (SlideLayout layout : candidates) {
            double score = calculateMatchScore(slide, layout);
            
            if (!usedLayoutIds.contains(layout.getLayoutId())) {
                score += 0.1;
            }
            
            if (score > bestScore) {
                bestScore = score;
                bestLayout = layout;
            }
        }
        
        return bestLayout;
    }
    
    private String mapSlideTypeToSemanticType(String slideType) {
        return switch (slideType.toLowerCase()) {
            case "cover" -> "TITLE_SLIDE";
            case "section" -> "SECTION_HEADER";
            case "content" -> "CONTENT";
            case "conclusion" -> "CONTENT";
            default -> "CONTENT";
        };
    }
    
    private double calculateMatchScore(EnrichedSlide slide, SlideLayout layout) {
        double score = 0.0;
        
        String targetType = mapSlideTypeToSemanticType(slide.getType());
        if (targetType.equals(layout.getSemanticType())) {
            score += 0.5;
        }
        
        if (layout.getZones() != null) {
            boolean hasTitle = layout.getZones().stream()
                    .anyMatch(z -> "title".equals(z.getZoneType()) || "center_title".equals(z.getZoneType()));
            boolean hasBody = layout.getZones().stream()
                    .anyMatch(z -> "body".equals(z.getZoneType()));
            boolean hasPicture = layout.getZones().stream()
                    .anyMatch(z -> "picture".equals(z.getZoneType()));
            
            if (slide.getTitle() != null && hasTitle) {
                score += 0.2;
            }
            
            if (slide.getBulletPoints() != null && !slide.getBulletPoints().isEmpty() && hasBody) {
                score += 0.2;
            }
            
            if (slide.getSubtitle() != null && hasBody) {
                score += 0.1;
            }
        }
        
        return score;
    }
    
    private void markAllAsDynamic(EnrichedPlan plan) {
        for (EnrichedSlide slide : plan.getSlides()) {
            slide.setDynamic(true);
            slide.setLayoutMatchScore(0.0);
        }
        plan.updateDynamicCount();
    }
}
