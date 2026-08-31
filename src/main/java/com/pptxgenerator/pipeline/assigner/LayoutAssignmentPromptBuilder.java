package com.pptxgenerator.pipeline.assigner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptxgenerator.common.ai.SystemPromptLibrary;
import com.pptxgenerator.model.LayoutAnalysis;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Builds the AI prompts used to assign a layout to a content slide: a system prompt describing
 * the visual-variety rules and an adéquation constraints, and a user prompt describing the slide
 * and the candidate layouts.
 */
@Slf4j
@ApplicationScoped
public class LayoutAssignmentPromptBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String buildSystemPrompt() {
        return SystemPromptLibrary.withJsonDirective(SystemPromptLibrary.expertIntro("design de présentations PowerPoint")
            + """

            Tu dois choisir le layout le plus adapté pour une slide en fonction de:
            - Son purpose (rôle narratif)
            - Son content brief (description du contenu)
            - Les layouts disponibles avec leurs descriptions enrichies
            - Les règles de variété visuelle

            Règles de variété visuelle:
            - Éviter d'utiliser le même layout 3 fois consécutivement
            - Privilégier la diversité visuelle

            Règles d'adéquation:
            - Comparaisons/avant-après -> TWO_COLUMN
            - Contenu dense/explicatif -> CONTENT (layouts avec grande zone body)
            - Contenu visuel/illustrations -> CONTENT_WITH_MEDIA
            - Métriques clés/chiffres -> CONTENT avec plusieurs zones 'line' ou 'word' ou 'body'

            NE JAMAIS utiliser les layouts des types suivants:
            - OUTLINE (réservés aux sommaires)
            - TITLE_SLIDE (réservés aux titres d'ouverture)
            - SECTION_HEADER (réservés aux transitions)
            - CUSTOM (non exploitables)
            - BLANK (vides)

            Réponds UNIQUEMENT avec un JSON valide:
            {
              "layout_id": "layout_X",
              "rationale": "Explication concise du choix (1-2 phrases)"
            }
            """);
    }

    public String buildUserPrompt(String purpose, String contentBrief,
                                  List<LayoutAnalysis> layoutsForAI, List<String> previousLayoutIds) {
        try {
            String layoutsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(layoutsForAI);
            String previousJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(previousLayoutIds);
            return """
                Slide à traiter:
                - Purpose: %s
                - Content brief: %s

                Layouts disponibles:
                %s

                Layouts utilisés récemment (à éviter si possible):
                %s

                Choisis le layout le plus adapté.
                """.formatted(
                    purpose != null ? purpose : "Non spécifié",
                    contentBrief != null ? contentBrief : "Non spécifié",
                    layoutsJson,
                    previousJson
            );
        } catch (Exception e) {
            log.error("Erreur sérialisation JSON: {}", e.getMessage());
            return "Slide à traiter: " + purpose;
        }
    }
}
