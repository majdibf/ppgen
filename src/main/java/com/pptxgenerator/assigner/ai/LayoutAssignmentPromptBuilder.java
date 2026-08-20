package com.pptxgenerator.assigner.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptxgenerator.assigner.model.ClassifiedLayout;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class LayoutAssignmentPromptBuilder {

    private final ObjectMapper objectMapper;

    /**
     * PROMPT IDENTIQUE AU SCRIPT PYTHON
     */
    public String buildSystemPrompt() {
        return """
            Tu es un expert en design de présentations.
            Tu dois choisir le layout le plus adapté à chaque slide.

            RÈGLES DE SÉLECTION :
            - CONTENU TEXTUEL (liste à puces, paragraphe) → CONTENT avec zone body
            - DONNÉES / CHIFFRES / MÉTRIQUES → CONTENT avec plusieurs zones
            - IMAGES / VISUELS → CONTENT_WITH_MEDIA
            - COMPARAISON AVANT/APRÈS → TWO_COLUMN

            LAYOUTS À ÉVITER :
            - OUTLINE (réservés aux sommaires)
            - TITLE_SLIDE (réservés aux titres)
            - SECTION_HEADER (réservés aux transitions)
            - CUSTOM (non exploitable)
            - BLANK (vides)

            Réponds UNIQUEMENT avec un JSON valide:
            {
              "layout_id": "layout_X",
              "rationale": "Explication concise du choix (1-2 phrases)"
            }
            """;
    }

    /**
     * PROMPT IDENTIQUE AU SCRIPT PYTHON
     */
    public String buildUserPrompt(String purpose, String contentBrief,
                                   List<ClassifiedLayout> layoutsForAI,
                                   List<String> previousLayoutIds) {
        try {
            String layoutsJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(layoutsForAI);
            String previousJson = objectMapper.writeValueAsString(previousLayoutIds);

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
