package com.pptxgenerator.common.ai;

/**
 * Prompt constants for the layout assignment stage (M3): the system prompt body describing the
 * visual-variety and adéquation rules, and the user prompt template describing the slide and its
 * candidate layouts. The {@code *PromptBuilder} owns the actual building logic.
 */
public enum LayoutAssignmentStagePrompt {
    LAYOUT_ASSIGNMENT;

    /** System prompt body (appended after the expert intro and JSON-only directive). */
    public static final String SYSTEM_BODY = """
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
        """;

    /** User prompt template: purpose, content brief, layouts JSON, recent layout ids. */
    public static final String USER_TEMPLATE = """
        Slide à traiter:
        - Purpose: %s
        - Content brief: %s

        Layouts disponibles:
        %s

        Layouts utilisés récemment (à éviter si possible):
        %s

        Choisis le layout le plus adapté.
        """;
}
