package com.pptxgenerator.generator.prompt;

import com.pptxgenerator.assigner.model.ClassifiedLayout;
import com.pptxgenerator.assigner.model.SlidePlanWithLayout;
import com.pptxgenerator.model.enums.ContentCapacity;
import com.pptxgenerator.model.enums.SemanticType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ContentPromptBuilder {

    /**
     * PROMPT SYSTÈME IDENTIQUE AU PYTHON + améliorations
     */
    public String buildSystemPrompt() {
        return """
            Tu es un expert en rédaction de présentations professionnelles.

            RÈGLES DE RÉDACTION :
            1. Titres : maximum 8 mots
            2. Bullets : maximum 12 mots chacun
            3. Style télégraphique (pas de phrases complètes, pas de point final)
            4. Utiliser des données concrètes (chiffres, pourcentages)
            5. Le titre de la slide doit être unique dans la présentation
            6. Pas de redondance : aucune information ne doit être répétée à l'identique entre deux slides
            7. Cohérence de ton : le ton doit être uniforme sur toute la présentation
            """;
    }

    /**
     * Construit le prompt utilisateur adapté au type de layout
     */
    public String buildUserPrompt(SlidePlanWithLayout slide,
                                   String previousSlideTitle,
                                   String nextSlidePurpose,
                                   String language,
                                   String tone,
                                   boolean webSearch) {

        ClassifiedLayout layout = slide.getLayout();
        SemanticType layoutType = layout.getSemanticType();
        ContentCapacity capacity = layout.getContentCapacity();

        int maxBullets = capacity.getMaxBullets();

        StringBuilder prompt = new StringBuilder();

        // Contexte de la slide
        prompt.append("SLIDE À RÉDIGER :\n");
        prompt.append("- Type : ").append(slide.getSlideType()).append("\n");
        prompt.append("- Purpose : ").append(slide.getPurpose()).append("\n");
        prompt.append("- Content Brief : ").append(slide.getContentBrief()).append("\n");
        prompt.append("- Detailed Context : ").append(slide.getDetailedContext()).append("\n\n");

        // Contexte de présentation (cohérence)
        if (previousSlideTitle != null) {
            prompt.append("SLIDE PRÉCÉDENTE : ").append(previousSlideTitle).append("\n");
        }
        if (nextSlidePurpose != null) {
            prompt.append("SLIDE SUIVANTE : ").append(nextSlidePurpose).append("\n\n");
        }

        // Layout assigné
        prompt.append("LAYOUT ASSIGNÉ :\n");
        prompt.append("- Type : ").append(layoutType).append("\n");
        prompt.append("- Capacité : ").append(capacity).append(" (max ").append(maxBullets).append(" bullets par zone)\n\n");

        // Contraintes
        prompt.append("CONTRAINTES :\n");
        prompt.append("- Ton : ").append(tone).append("\n");
        prompt.append("- Langue : ").append(language).append("\n");
        prompt.append("- Web Search : ").append(webSearch).append("\n\n");

        // Format de sortie adapté au type de layout
        prompt.append("FORMAT DE SORTIE (JSON) :\n");
        prompt.append(buildOutputFormatExample(layoutType, maxBullets));

        return prompt.toString();
    }

    /**
     * Construit un exemple de format de sortie adapté au type de layout
     */
    private String buildOutputFormatExample(SemanticType layoutType, int maxBullets) {
        return switch (layoutType) {
            case TITLE_SLIDE -> """
                {
                  "title": "Titre principal (max 8 mots)",
                  "subtitle": "Sous-titre contextuel"
                }
                """;

            case SECTION_HEADER -> """
                {
                  "title": "Titre de section (max 8 mots)",
                  "subtitle": "Phrase d'accroche optionnelle"
                }
                """;

            case CONTENT -> """
                {
                  "title": "Titre de la slide (max 8 mots)",
                  "body": {
                    "header": "Sous-titre optionnel",
                    "bullets": ["bullet 1 (max 12 mots)", "bullet 2", "..."]
                  }
                }
                Note : Maximum %d bullets
                """.formatted(maxBullets);

            case TWO_COLUMN -> """
                {
                  "title": "Titre de la slide (max 8 mots)",
                  "left_column": {
                    "header": "Titre colonne gauche",
                    "bullets": ["élément 1", "élément 2"]
                  },
                  "right_column": {
                    "header": "Titre colonne droite",
                    "bullets": ["élément 1", "élément 2"]
                  }
                }
                Note : Maximum %d bullets par colonne
                """.formatted(maxBullets);

            case CONTENT_WITH_MEDIA -> """
                {
                  "title": "Titre de la slide (max 8 mots)",
                  "body": {
                    "bullets": ["bullet 1", "bullet 2"]
                  },
                  "media_description": "Description textuelle de l'image/graphique souhaité"
                }
                Note : Maximum %d bullets
                """.formatted(maxBullets);

            case CUSTOM -> """
                {
                  "title": "Titre de la slide (max 8 mots)",
                  "box_1": {
                    "metric": "-15%",
                    "label": "Émissions CO₂"
                  },
                  "box_2": {
                    "metric": "×4",
                    "label": "Énergie renouvelable"
                  },
                  "box_3": {
                    "metric": "72/100",
                    "label": "Score ESG"
                  }
                }
                Note : Adapter selon les zones disponibles dans le layout
                """;

            case OUTLINE, BLANK, PENDING -> """
                {
                  "title": "Titre de la slide (max 8 mots)",
                  "body": {
                    "bullets": ["élément 1", "élément 2"]
                  }
                }
                Note : Maximum %d bullets
                """.formatted(maxBullets);
        };
    }
}
