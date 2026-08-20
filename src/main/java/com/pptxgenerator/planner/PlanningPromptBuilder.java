package com.pptxgenerator.planner;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class PlanningPromptBuilder {

    /**
     * PROMPT IDENTIQUE AU SCRIPT PYTHON
     */
    public String buildSystemPrompt() {
        return """
            Tu es un expert en création de présentations professionnelles.

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
    }

    /**
     * PROMPT IDENTIQUE AU SCRIPT PYTHON
     */
    public String buildUserPrompt(String instructions, List<String> inputs,
                                  int minSlides, int maxSlides,
                                  String language, String tone) {

        String contextBlock = (inputs == null || inputs.isEmpty())
            ? "Aucun contexte fourni."
            : inputs.stream()
                .map(input -> "- " + input)
                .collect(Collectors.joining("\n"));

        return """
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
            """.formatted(instructions, contextBlock, minSlides, maxSlides);
    }

    /**
     * OUTPUT SCHEMA IDENTIQUE AU PYTHON
     * Utilisé pour contraindre la sortie JSON de l'IA (si le LLM supporte json_schema)
     */
    public String buildOutputSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "presentation_plan": {
                  "type": "object",
                  "properties": {
                    "title": {"type": "string"},
                    "narrative_arc": {"type": "string"},
                    "total_slides": {"type": "integer"},
                    "slides": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "slide_number": {"type": "integer"},
                          "slide_type": {
                            "type": "string",
                            "enum": ["title", "outline", "section_transition", "content"]
                          },
                          "purpose": {"type": "string"},
                          "content_brief": {"type": "string"},
                          "detailed_context": {"type": "string"},
                          "section_number": {"type": "integer"},
                          "section_title": {"type": "string"}
                        },
                        "required": [
                          "slide_number",
                          "slide_type",
                          "purpose",
                          "content_brief",
                          "detailed_context"
                        ]
                      }
                    }
                  },
                  "required": ["title", "narrative_arc", "total_slides", "slides"]
                }
              },
              "required": ["presentation_plan"]
            }
            """;
    }
}
