package com.pptxgenerator.common.ai;

/**
 * Prompt constants for the content generation stage (M4), aligned with the POC
 * (pocadel/deepseek_python_20260904_752718.py). Only "content" slides reach this
 * stage: outline and section_transition slides are generated deterministically
 * (see SlideContentGenerator).
 */
public enum ContentStagePrompt {
    CONTENT_GENERATION;

    /** System prompt body (appended after the expert intro and JSON-only directive). */
    public static final String SYSTEM_BODY = """
        Ta mission est de rédiger le CONTENU EXACT d'une slide PowerPoint.

        RÈGLES DE RÉDACTION:

        1. ZONES DE TYPE 'title', 'subtitle', 'center_title'
        - Maximum 8 mots
        - Clair et impactant
        - Unique dans la présentation

        2. ZONES DE TYPE 'word'
        - Texte très court (1-3 caractères, chiffre, lettre, ou expression courte)
        - Utiliser le contexte de la zone_description pour déterminer l'usage
        - Exemples: "01", "02", "A", "B", "Contexte", "Objectif"

        3. ZONES DE TYPE 'line'
        - Texte court sur une seule ligne
        - Maximum 10 mots
        - JAMAIS plus d'une ligne
        - Style télégraphique
        - Privilégier les chiffres et métriques

        4. ZONES DE TYPE 'body'
        - Texte libre multilingue
        - Paragraphes, listes à puces, phrases complètes autorisées
        - Adapter la densité à la surface de la zone (indiquée dans la description)
        - Utiliser des tirets (-) ou puces (+) pour les listes
        - Privilégier les chiffres et données concrètes du contexte

        5. ZONES DE TYPE 'picture', 'background', 'unknown_X'
        - Laisser VIDE (chaîne vide "")

        6. STYLE GÉNÉRAL
        - Ton professionnel et factuel
        - Pas de markdown (**, ##, etc.)
        - AUCUNE omission de données du detailed_context
        - Utiliser TOUS les chiffres, dates, noms fournis
        - Respecter STRICTEMENT la limite "Max characters" indiquée pour chaque zone
          (nombre de caractères, espaces inclus); si le contexte est trop riche,
          synthétiser ou sélectionner les points les plus importants plutôt que déborder

        7. FORMAT DE SORTIE
        - Clés au format: {zone_type}_{zone_id}
        - Valeurs: toujours des strings
        - Respecter EXACTEMENT le schéma fourni

        Génère maintenant le contenu exact au format JSON.
        """;
}
