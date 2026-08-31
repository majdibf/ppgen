package com.pptxgenerator.pipeline.generator;

import com.pptxgenerator.common.ai.SystemPromptLibrary;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.ZoneKeys;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Builds the AI prompts used to fill a content slide: a system prompt describing the per-zone-type
 * writing rules, and a user prompt enumerating the zones to fill (with their descriptions and
 * maximum lengths).
 */
@Slf4j
@ApplicationScoped
public class ContentPromptBuilder {

    public String buildSystemPrompt() {
        return SystemPromptLibrary.withJsonDirective(SystemPromptLibrary.expertIntro("rédaction de présentations professionnelles")
            + """

            Ta mission est de rédiger le CONTENU EXACT d'une slide PowerPoint.

            RÈGLES DE RÉDACTION:

            1. ZONES DE TYPE 'title', 'subtitle', 'center_title'
            - Maximum 5 mots
            - Clair et impactant
            - Unique dans la présentation

            2. ZONES DE TYPE 'word'
            - Texte très court (1-3 caractères, chiffre, lettre, ou expression courte)
            - Utiliser le contexte de la zone_description pour déterminer l'usage
            - Exemples: "01", "02", "A", "B", "Contexte", "Objectif"
            - Si la description indique [Max X caractères], le texte généré NE DOIT PAS dépasser X caractères

            3. ZONES DE TYPE 'line'
            - Texte court sur une seule ligne
            - Maximum 10 mots
            - JAMAIS plus d'une ligne
            - Style télégraphique
            - Privilégier les chiffres et métriques
            - Si la description indique [Max X caractères], le texte généré NE DOIT PAS dépasser X caractères

            4. ZONES DE TYPE 'body'
            - Texte multilingue avec listes à puces
            - Maximum 5-6 bullets par zone
            - Chaque bullet : maximum 12 mots
            - Utiliser des tirets (-) ou puces (+) pour les listes
            - Privilégier les chiffres et données concrètes du contexte
            - IMPORTANT : Être concis, le texte doit tenir dans la zone sans déborder
            - Si la description indique [Max X caractères], le texte généré NE DOIT PAS dépasser X caractères

            5. ZONES DE TYPE 'picture', 'background', 'unknown_X'
            - Laisser VIDE (chaîne vide "")

            6. STYLE GÉNÉRAL
            - Ton professionnel et factuel
            - Pas de markdown (**, ##, etc.)
            - AUCUNE omission de données du detailed_context
            - Utiliser TOUS les chiffres, dates, noms fournis
            - CONTRAINTE CRITIQUE: Si une zone indique [Max X caractères], le texte généré NE DOIT PAS dépasser X caractères

            7. FORMAT DE SORTIE
            - Clés au format: {zone_type}_{zone_id}
            - Valeurs: toujours des strings
            - Respecter EXACTEMENT le schéma fourni

            Génère maintenant le contenu exact au format JSON.""");
    }

    public String buildUserPrompt(com.pptxgenerator.pipeline.assigner.model.SlidePlanWithLayout slide,
                                  String previousSlideTitle,
                                  String nextSlidePurpose,
                                  String language,
                                  String tone,
                                  boolean webSearch,
                                  List<Zone> layoutZones) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("CONTEXTE DE LA PRÉSENTATION:\n");
        if (previousSlideTitle != null) {
            prompt.append("- Slide précédente: ").append(previousSlideTitle).append("\n");
        }
        if (nextSlidePurpose != null) {
            String truncated = nextSlidePurpose.length() > 100 ? nextSlidePurpose.substring(0, 100) + "..." : nextSlidePurpose;
            prompt.append("- Slide suivante: ").append(truncated).append("\n");
        }

        prompt.append("\nINFORMATIONS SUR CETTE SLIDE:\n");
        prompt.append("- Numéro: ").append(slide.getSlideNumber()).append("\n");
        prompt.append("- Type: ").append(slide.getSlideType()).append("\n");
        prompt.append("- Purpose: ").append(slide.getPurpose()).append("\n");
        prompt.append("- Content brief: ").append(slide.getContentBrief()).append("\n");

        prompt.append("\nCONTEXTE DÉTAILLÉ (UTILISER TOUTES CES DONNÉES):\n");
        if (slide.getDetailedContext() != null && !slide.getDetailedContext().isBlank()) {
            prompt.append(slide.getDetailedContext()).append("\n");
        }

        prompt.append("\nZONES À REMPLIR:\n");
        for (Zone zone : layoutZones) {
            String zoneKey = ZoneKeys.key(zone);
            String desc = zone.getZoneDescription() != null ? zone.getZoneDescription() : "Zone de type " + zone.getZoneType().getValue();
            prompt.append("- ").append(zoneKey).append(":\n");
            prompt.append("  Type: ").append(zone.getZoneType().getValue()).append("\n");
            prompt.append("  Description: ").append(desc).append("\n");
            prompt.append("  max caractères: ").append(zone.getMaxCharacters()).append("\n");
        }

        prompt.append("\nInstruction finale: \nGénère maintenant le contenu exact pour chaque zone au format JSON.");
        return prompt.toString();
    }
}
