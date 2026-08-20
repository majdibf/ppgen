package com.pptxgenerator.assigner.rule;

import com.pptxgenerator.assigner.model.ClassifiedLayout;
import com.pptxgenerator.assigner.model.SlidePlanWithLayout;
import com.pptxgenerator.model.enums.SemanticType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Règle L1 : Pas de triple répétition.
 * Un même type de layout ne peut pas apparaître plus de 2 fois consécutivement.
 */
@Slf4j
@ApplicationScoped
public class VarietyEnforcer {

    /**
     * Vérifie si le layout choisi respecte la règle L1.
     * Si les 2 slides précédentes ont le même semantic_type, le layout doit être différent.
     *
     * @return true si le layout est acceptable, false s'il viole L1
     */
    public boolean respectsVariety(ClassifiedLayout candidate, List<SlidePlanWithLayout> previousSlides) {
        if (previousSlides.size() < 2) return true;

        SemanticType candidateType = candidate.getSemanticType();
        SlidePlanWithLayout prev1 = previousSlides.get(previousSlides.size() - 1);
        SlidePlanWithLayout prev2 = previousSlides.get(previousSlides.size() - 2);

        if (prev1.getLayout() == null || prev2.getLayout() == null) return true;

        SemanticType type1 = prev1.getLayout().getSemanticType();
        SemanticType type2 = prev2.getLayout().getSemanticType();

        // Si les 2 précédents sont identiques ET identiques au candidat → violation
        return !(type1 == candidateType && type2 == candidateType);
    }

    /**
     * Exclut les layouts qui violeraient la règle L1.
     */
    public List<ClassifiedLayout> excludeViolatingLayouts(List<ClassifiedLayout> candidates,
                                                           List<SlidePlanWithLayout> previousSlides) {
        if (previousSlides.size() < 2) return candidates;

        SlidePlanWithLayout prev1 = previousSlides.get(previousSlides.size() - 1);
        SlidePlanWithLayout prev2 = previousSlides.get(previousSlides.size() - 2);

        if (prev1.getLayout() == null || prev2.getLayout() == null) return candidates;

        SemanticType type1 = prev1.getLayout().getSemanticType();
        SemanticType type2 = prev2.getLayout().getSemanticType();

        // Si les 2 précédents sont identiques, exclure ce type
        if (type1 == type2) {
            return candidates.stream()
                .filter(l -> l.getSemanticType() != type1)
                .toList();
        }

        return candidates;
    }
}
