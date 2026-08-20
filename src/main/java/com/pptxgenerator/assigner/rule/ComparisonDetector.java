package com.pptxgenerator.assigner.rule;

import com.pptxgenerator.assigner.model.ClassifiedLayout;
import com.pptxgenerator.model.enums.SemanticType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Règle L3 : Si le purpose mentionne une comparaison, privilégier TWO_COLUMN.
 */
@Slf4j
@ApplicationScoped
public class ComparisonDetector {

    // Patterns de détection de comparaison (FR + EN)
    private static final List<Pattern> COMPARISON_PATTERNS = List.of(
        Pattern.compile("\\b(avant|après|comparaison|comparer|versus|vs\\.?|face à|en regard|mettre en regard)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(before|after|comparison|compare|versus|vs\\.?|side by side)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(20\\d{2}\\s*(vs|versus|→|->|versus)\\s*20\\d{2})\\b"), // "2024 vs 2026"
        Pattern.compile("\\b(pour|contre|avantages|inconvénients|forces|faiblesses|pros|cons)\\b", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Détecte si le purpose indique une comparaison.
     */
    public boolean isComparison(String purpose, String contentBrief) {
        String text = (purpose != null ? purpose : "") + " " + (contentBrief != null ? contentBrief : "");
        return COMPARISON_PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }

    /**
     * Tente d'attribuer un layout TWO_COLUMN si une comparaison est détectée.
     */
    public Optional<ClassifiedLayout> tryAssignTwoColumn(String purpose, String contentBrief,
                                                          List<ClassifiedLayout> layouts) {
        if (!isComparison(purpose, contentBrief)) {
            return Optional.empty();
        }

        return layouts.stream()
            .filter(l -> l.getSemanticType() == SemanticType.TWO_COLUMN)
            .findFirst();
    }
}
