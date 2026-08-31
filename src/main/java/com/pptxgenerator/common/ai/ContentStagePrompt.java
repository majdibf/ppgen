package com.pptxgenerator.common.ai;

/**
 * Prompt constants for the content generation stage (M4): the system prompt body with the
 * per-zone-type writing rules. The {@code ContentPromptBuilder} owns the actual building logic
 * for both system and user prompts.
 */
public enum ContentStagePrompt {
    CONTENT_GENERATION;

    /** System prompt body (appended after the expert intro and JSON-only directive). */
    public static final String SYSTEM_BODY = """
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

        Génère maintenant le contenu exact au format JSON.
        """;
}
