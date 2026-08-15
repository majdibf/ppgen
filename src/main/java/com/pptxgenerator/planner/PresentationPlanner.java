package com.pptxgenerator.planner;

import com.pptxgenerator.client.GenerativeAiGateway;
import com.pptxgenerator.client.builder.GenerativeAiRequestBuilder;
import com.pptxgenerator.client.dto.JsonSchemaDto;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import com.pptxgenerator.model.PresentationPlan;
import com.pptxgenerator.model.TemplateStructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@ApplicationScoped
public class PresentationPlanner {

    @Inject
    public GenerativeAiGateway aiGateway;

    @Inject
    public AiResponseParser responseParser;

    public PresentationPlan generatePlan(String topic, TemplateStructure template) {
        log.info("Generating presentation plan for topic: {}", topic);

        TextRequestDto request = buildRequest(topic, template);
        TextResponseDto response = aiGateway.processRequest(request);

        String responseText = response.getCandidates().get(0).getText();
        log.debug("AI response: {}", responseText);

        PresentationPlan plan = responseParser.parseAs(responseText, PresentationPlan.class);
        log.info("Generated plan with {} slides", plan.getSlides().size());

        return plan;
    }

    private TextRequestDto buildRequest(String topic, TemplateStructure template) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(topic, template);
        JsonSchemaDto schema = buildOutputSchema();

        return GenerativeAiRequestBuilder.builder()
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .outputSchema(schema)
                .temperature(0.7)
                .build()
                .toRequest();
    }

    private String buildSystemPrompt() {
        return """
                Tu es un planificateur de présentations professionnel.
                Tu génères des plans de présentation structurés en JSON.
                
                IMPORTANT: Tu dois répondre UNIQUEMENT en JSON valide, sans texte supplémentaire, sans markdown, sans explications.
                
                Règles:
                - Le JSON doit avoir exactement cette structure: {"title": "...", "slide_count": N, "slides": [...]}
                - Utilise EXACTEMENT ces noms de champs en ANGLAIS: "title", "slide_count", "slides", "type", "subtitle", "bullet_points"
                - Chaque slide a un type: "cover", "section", "content", ou "conclusion".
                - Le premier slide est toujours de type "cover".
                - Le dernier slide est toujours de type "conclusion".
                - Les slides intermédiaires sont de type "content" ou "section".
                - Chaque slide a un "title" obligatoire (en anglais ou français, mais le champ doit s'appeler "title").
                - Les slides de type "cover" peuvent avoir un "subtitle".
                - Les slides de type "content" ont des "bullet_points" (liste de points clés).
                - NE réponds PAS avec d'autres noms de champs comme "titre", "sous_titre", etc.
                - Réponds UNIQUEMENT avec le JSON, rien d'autre.
                """;
    }

    private String buildUserPrompt(String topic, TemplateStructure template) {
        int layoutCount = template.getLayouts() != null ? template.getLayouts().size() : 0;

        return String.format("""
                Génère un plan de présentation sur le sujet suivant: "%s"
                
                Informations sur le template:
                - Nombre de layouts disponibles: %d
                - Titre suggéré: utilise le sujet fourni
                
                Génère un plan avec 5 à 8 slides bien structurés.
                """, topic, layoutCount);
    }

    private JsonSchemaDto buildOutputSchema() {
        JsonSchemaDto slideSchema = JsonSchemaDto.builder()
                .type(JsonSchemaDto.TypeEnum.OBJECT)
                .properties(new LinkedHashMap<>(Map.of(
                        "type", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.STRING)
                                .enum_(List.of("cover", "section", "content", "conclusion"))
                                .build(),
                        "title", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.STRING)
                                .maxLength(100)
                                .build(),
                        "subtitle", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.STRING)
                                .maxLength(200)
                                .build(),
                        "bullet_points", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.ARRAY)
                                .items(JsonSchemaDto.builder()
                                        .type(JsonSchemaDto.TypeEnum.STRING)
                                        .build())
                                .minItems(1)
                                .maxItems(10)
                                .build(),
                        "notes", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.STRING)
                                .build(),
                        "layout_hint", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.STRING)
                                .build()
                )))
                .required(List.of("type", "title"))
                .build();

        return JsonSchemaDto.builder()
                .type(JsonSchemaDto.TypeEnum.OBJECT)
                .properties(new LinkedHashMap<>(Map.of(
                        "title", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.STRING)
                                .build(),
                        "slide_count", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.INTEGER)
                                .build(),
                        "slides", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.ARRAY)
                                .items(slideSchema)
                                .minItems(3)
                                .maxItems(15)
                                .build()
                )))
                .required(List.of("title", "slides"))
                .build();
    }
}
