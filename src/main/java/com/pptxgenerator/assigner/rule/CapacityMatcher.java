package com.pptxgenerator.assigner.rule;

import com.pptxgenerator.assigner.model.ClassifiedLayout;
import com.pptxgenerator.model.enums.ContentCapacity;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Règle L2 : Adéquation capacité/densité.
 * - content_brief dense (beaucoup de contenu attendu) → layout HIGH
 * - content_brief léger → LOW ou MEDIUM
 */
@Slf4j
@ApplicationScoped
public class CapacityMatcher {

    // Indices de densité dans le content_brief
    private static final Pattern DENSE_INDICATORS = Pattern.compile(
        "\\b(\\d+\\s*(points|éléments|indicateurs|kpi|chiffres|métriques|initiatives|axes)|" +
        "liste (complète|détaillée)|détailler|exhaustif|plusieurs|" +
        "\\d+ à \\d+|" + // "3 à 4", "4 à 5"
        "(5|6|7|8|9|10|11|12)\\s*(bullets|points|éléments))\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LIGHT_INDICATORS = Pattern.compile(
        "\\b(résumé|synthèse|bref|concis|1\\s*(point|élément|chiffre)|" +
        "introduction|conclusion|accroche|" +
        "(1|2)\\s*(bullets|points|éléments))\\b",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Estime la densité attendue du contenu.
     */
    public EstimatedDensity estimateDensity(String contentBrief) {
        if (contentBrief == null || contentBrief.isBlank()) {
            return EstimatedDensity.MEDIUM;
        }

        boolean hasDense = DENSE_INDICATORS.matcher(contentBrief).find();
        boolean hasLight = LIGHT_INDICATORS.matcher(contentBrief).find();

        if (hasDense && !hasLight) return EstimatedDensity.HIGH;
        if (hasLight && !hasDense) return EstimatedDensity.LOW;
        return EstimatedDensity.MEDIUM;
    }

    /**
     * Filtre les layouts selon la densité estimée.
     * Retourne les layouts compatibles (capacité >= densité estimée).
     */
    public List<ClassifiedLayout> filterByDensity(List<ClassifiedLayout> layouts, EstimatedDensity density) {
        return layouts.stream()
            .filter(l -> isCapacityCompatible(l.getContentCapacity(), density))
            .toList();
    }

    private boolean isCapacityCompatible(ContentCapacity capacity, EstimatedDensity density) {
        if (capacity == null) return true;
        return switch (density) {
            case HIGH -> capacity == ContentCapacity.HIGH;
            case MEDIUM -> capacity == ContentCapacity.HIGH || capacity == ContentCapacity.MEDIUM;
            case LOW -> true; // Tout est compatible avec LOW
        };
    }

    public enum EstimatedDensity {
        HIGH, MEDIUM, LOW
    }
}
