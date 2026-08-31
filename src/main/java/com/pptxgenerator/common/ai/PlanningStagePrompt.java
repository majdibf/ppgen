package com.pptxgenerator.common.ai;

/**
 * Prompt constants for the planning stage (M2): the system prompt body and the user prompt
 * template used to generate a narrative presentation plan. The {@code PlanningPromptBuilder}
 * owns the actual building logic.
 */
public enum PlanningStagePrompt {
    PLAN_GENERATION;

    /** System prompt body (appended after the expert intro and JSON-only directive). */
    public static final String SYSTEM_BODY = """
        RÔLE :
        Tu dois générer un plan narratif structuré pour une présentation PowerPoint.

        RÈGLES DE QUALITÉ :
        1. Chaque slide ne porte qu'une seule idée principale
        2. Les slides doivent s'enchaîner logiquement
        3. Alterner slides denses (content) et slides légères (transitions)
        4. La présentation commence par une slide "title" et se termine par une slide de conclusion

        TYPES DE SLIDES AUTORISÉS :
        - "title" : Slide de couverture
        - "outline" : Sommaire (si plus de 8 slides)
        - "section_transition" : Transition entre sections
        - "content" : Slide de contenu standard
        """;

    /** User prompt template: instructions, context, min/max slide count. */
    public static final String USER_TEMPLATE = """
        INSTRUCTIONS UTILISATEUR :
        %s

        CONTEXTE FOURNI :
        %s

        CONTRAINTES :
        - Nombre de slides : entre %d et %d
        - La première slide doit être de type "title"
        - Inclure un sommaire (type "outline") si la présentation fait plus de 8 slides
        - Utiliser des transitions (type "section_transition") pour marquer les grandes parties
        - Numéroter les sections séquentiellement (section_number: 1, 2, 3...)
        - Le "detailed_context" doit contenir TOUTES les données factuelles nécessaires

        FORMAT DE SORTIE (JSON) :
        {
          "presentation_plan": {
            "title": "Titre de la présentation",
            "narrative_arc": "Description de l'arc narratif",
            "total_slides": 0,
            "slides": [
              {
                "slide_number": 1,
                "slide_type": "title",
                "purpose": "Rôle narratif de cette slide",
                "content_brief": "Description synthétique du contenu attendu",
                "detailed_context": "Toutes les informations factuelles nécessaires"
              }
            ]
          }
        }

        Génère maintenant le plan narratif complet au format JSON.
        """;
}
