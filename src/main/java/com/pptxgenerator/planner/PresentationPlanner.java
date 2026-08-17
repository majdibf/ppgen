package com.pptxgenerator.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pptxgenerator.client.GenerativeAiGateway;
import com.pptxgenerator.client.builder.GenerativeAiRequestBuilder;
import com.pptxgenerator.client.dto.JsonSchemaDto;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import com.pptxgenerator.dto.request.ContentOptions;
import com.pptxgenerator.dto.request.InputContent;
import com.pptxgenerator.model.PresentationPlan;
import com.pptxgenerator.model.PlanSlide;
import com.pptxgenerator.model.TemplateStructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class PresentationPlanner {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public GenerativeAiGateway aiGateway;

    @Inject
    public AiResponseParser responseParser;

    public PresentationPlan generatePlan(String topic, TemplateStructure template) {
        return generatePlan(topic, Collections.emptyList(), null, template);
    }

    public PresentationPlan generatePlan(String instructions, List<InputContent> inputs,
                                         ContentOptions options, TemplateStructure template) {
        log.info("Generating presentation plan");

        String language = options != null && options.getLanguage() != null ? options.getLanguage() : "fr";
        String tone = options != null && options.getTone() != null
            ? options.getTone().name().toLowerCase() : "professional";
        int minSlides = options != null && options.getNumSlides() != null
            && options.getNumSlides().getMin() != null ? options.getNumSlides().getMin() : 5;
        int maxSlides = options != null && options.getNumSlides() != null
            && options.getNumSlides().getMax() != null ? options.getNumSlides().getMax() : 15;
        if (minSlides < 1 || maxSlides < minSlides) {
            throw new IllegalArgumentException("Invalid slide range");
        }

        TextRequestDto request = buildRequest(instructions, inputs, language, tone, minSlides, maxSlides);
        PresentationPlan plan = null;
        Exception lastParseError = null;
        for (int attempt = 1; attempt <= 2 && plan == null; attempt++) {
            TextResponseDto response = aiGateway.processRequest(request);
            String responseText = response.getCandidates().get(0).getText();
            log.debug("AI planning response attempt {}: {}", attempt, responseText);
            try {
                plan = parsePlanResponse(responseText, instructions);
            } catch (Exception e) {
                lastParseError = e;
                log.warn("Planning response parsing failed on attempt {}: {}", attempt, e.getMessage());
            }
        }
        if (plan == null) {
            throw new RuntimeException("Failed to parse AI planning response", lastParseError);
        }
        String sourceContext = inputs == null ? "" : inputs.stream()
            .map(InputContent::getText).filter(java.util.Objects::nonNull)
            .collect(Collectors.joining("\n"));
        normalizePlan(plan, instructions + "\n" + sourceContext, minSlides, maxSlides);
        log.info("Generated plan with {} slides", plan.getSlides().size());

        return plan;
    }

    private PresentationPlan parsePlanResponse(String rawResponse, String instructions) {
        try {
            JsonNode json = objectMapper.readTree(responseParser.extractJson(rawResponse));
            if (json.isArray()) {
                PresentationPlan plan = new PresentationPlan();
                plan.setTitle(instructions);
                plan.setSlides(objectMapper.convertValue(normalizeSlides(json),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, PlanSlide.class)));
                plan.setSlideCount(plan.getSlides().size());
                return plan;
            }
            if (json.isObject() && json.has("slides")) {
                ((ObjectNode) json).set("slides", normalizeSlides(json.get("slides")));
            }
            PresentationPlan plan = objectMapper.treeToValue(json, PresentationPlan.class);
            if (plan.getTitle() == null || plan.getTitle().isBlank()) {
                plan.setTitle(instructions);
            }
            return plan;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI response", e);
        }
    }

    private JsonNode normalizeSlides(JsonNode slides) {
        if (slides == null || !slides.isArray()) return slides;
        for (JsonNode slide : slides) {
            if (!slide.isObject()) continue;
            ObjectNode object = (ObjectNode) slide;

            // Groq may use the legacy title/body/content shape despite the schema.
            if (!object.has("purpose") || object.get("purpose").isNull()
                || object.get("purpose").asText().isBlank()) {
                if (object.has("title")) {
                    object.put("purpose", object.get("title").asText());
                } else if (object.has("slide_type")) {
                    object.put("purpose", object.get("slide_type").asText());
                } else if (object.has("type")) {
                    object.put("purpose", object.get("type").asText());
                }
            }

            if (!object.has("content_brief") || object.get("content_brief").isNull()
                || object.get("content_brief").asText().isBlank()) {
                JsonNode source = object.has("title") ? object.get("title")
                    : object.has("content") ? object.get("content")
                    : object.has("body") ? object.get("body") : object.get("points");
                if (source != null) object.put("content_brief", compactJsonValue(source));
            }

            if ((!object.has("purpose") || object.get("purpose").asText().equalsIgnoreCase(
                    object.path("type").asText())) && object.has("content_brief")) {
                object.put("purpose", object.get("content_brief").asText());
            }

            if (slide.isObject() && slide.has("detailed_context")
                && !slide.get("detailed_context").isTextual()) {
                object.put("detailed_context", slide.get("detailed_context").toString());
            } else if (!object.has("detailed_context") || object.get("detailed_context").isNull()
                || object.get("detailed_context").asText().isBlank()) {
                JsonNode source = object.has("body") ? object.get("body")
                    : object.has("content") ? object.get("content")
                    : object.has("points") ? object.get("points")
                    : object.has("title") ? object.get("title") : null;
                if (source != null) object.put("detailed_context", compactJsonValue(source));
            }
        }
        return slides;
    }

    private String compactJsonValue(JsonNode value) {
        if (value == null) return "";
        if (value.isArray()) {
            return String.join("; ", java.util.stream.StreamSupport.stream(value.spliterator(), false)
                .map(JsonNode::asText).toList());
        }
        return value.isTextual() ? value.asText() : value.toString();
    }

    private TextRequestDto buildRequest(String instructions, List<InputContent> inputs,
                                        String language, String tone, int minSlides, int maxSlides) {
        String systemPrompt = buildSystemPrompt(language, tone);
        String userPrompt = buildUserPrompt(instructions, inputs, minSlides, maxSlides);
        JsonSchemaDto schema = buildOutputSchema(minSlides, maxSlides);

        return GenerativeAiRequestBuilder.builder()
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .outputSchema(schema)
                .temperature(0.7)
                // A 20-slide plan needs substantially more output than the default 5-slide plan.
                .maxTokens(Math.min(7000, Math.max(1800, maxSlides * 320)))
                .build()
                .toRequest();
    }

    private String buildSystemPrompt(String language, String tone) {
        return String.format("""
            Tu es un expert en création de présentations professionnelles.
            Ta mission est de créer un PLAN NARRATIF, jamais le contenu final.
            Réponds UNIQUEMENT avec un JSON valide, sans markdown ni explication.

            RÈGLES:
            - Un seul message principal par slide.
            - Progression cohérente: contexte → problème → solution → résultats → perspective.
            - Ne jamais enchaîner plus de 3 slides content sans respiration.
            - Types exacts: title, outline, section_transition, content.
            - La première slide est title; outline suit title si plusieurs slides.
            - Chaque section_transition contient section_number séquentiel.
            - detailed_context contient toutes les données factuelles nécessaires.
            - Retourne exactement un objet racine avec `title`, `narrative_arc`, `total_slides` et `slides`.
            - Ne retourne jamais un tableau racine ni des objets `content` répétés.
            - Reste compact : detailed_context doit tenir en 1 à 2 phrases par slide.
            - Langue: %s
            - Ton: %s
            """, language, tone);
    }

    private String buildUserPrompt(String instructions, List<InputContent> inputs,
                                   int minSlides, int maxSlides) {
        String context = inputs == null || inputs.isEmpty() ? "Aucun contexte fourni" : inputs.stream()
            .map(InputContent::getText).filter(java.util.Objects::nonNull)
            .collect(Collectors.joining("\n\n"));
        return String.format("""
            INSTRUCTIONS UTILISATEUR:
            %s

            CONTEXTE FOURNI:
            %s

            CONTRAINTES:
            - Nombre de slides: entre %d et %d inclus
            - Première slide: title
            - Ajouter outline après title
            - Ajouter des section_transition pour les parties majeures
            Génère le plan narratif complet maintenant.
            """, instructions, context, minSlides, maxSlides);
    }

    private JsonSchemaDto buildOutputSchema(int minSlides, int maxSlides) {
        JsonSchemaDto slideSchema = JsonSchemaDto.builder()
                .type(JsonSchemaDto.TypeEnum.OBJECT)
                .properties(new LinkedHashMap<>(Map.of(
                        "slide_number", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.INTEGER).build(),
                        "slide_type", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.STRING)
                                .enum_(List.of("title", "outline", "section_transition", "content"))
                                .build(),
                        "purpose", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.STRING)
                                .build(),
                        "content_brief", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.STRING)
                                .build(),
                        "detailed_context", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.STRING)
                                .build(),
                        "section_number", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.INTEGER)
                                .build()
                )))
                .required(List.of("slide_number", "slide_type", "purpose", "content_brief", "detailed_context"))
                .build();

        return JsonSchemaDto.builder()
                .type(JsonSchemaDto.TypeEnum.OBJECT)
                .properties(new LinkedHashMap<>(Map.of(
                        "title", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.STRING)
                                .build(),
                        "narrative_arc", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.STRING)
                                .build(),
                        "total_slides", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.INTEGER)
                                .build(),
                        "slides", JsonSchemaDto.builder()
                                .type(JsonSchemaDto.TypeEnum.ARRAY)
                                .items(slideSchema)
                                .minItems(minSlides)
                                .maxItems(maxSlides)
                                .build()
                )))
                .required(List.of("title", "slides"))
                .build();
    }

    private void normalizePlan(PresentationPlan plan, String instructions, int minSlides, int maxSlides) {
        if (plan.getSlides() == null) {
            plan.setSlides(new ArrayList<>());
        }
        List<PlanSlide> slides = new ArrayList<>(plan.getSlides());
        if (slides.size() > maxSlides) {
            slides = slides.subList(0, maxSlides);
        }
        while (slides.size() < minSlides) {
            PlanSlide filler = new PlanSlide();
            filler.setSlideNumber(slides.size() + 1);
            filler.setSlideType("content");
            filler.setPurpose("Approfondir le sujet demandé");
            filler.setContentBrief(instructions);
            filler.setDetailedContext(instructions);
            slides.add(filler);
        }
        for (int i = 0; i < slides.size(); i++) {
            if (slides.get(i).getSlideNumber() == null) slides.get(i).setSlideNumber(i + 1);
            if (slides.get(i).getPurpose() == null || slides.get(i).getPurpose().isBlank()) {
                slides.get(i).setPurpose(slides.get(i).getContentBrief());
            }
            if (slides.get(i).getDetailedContext() == null || slides.get(i).getDetailedContext().isBlank()) {
                slides.get(i).setDetailedContext(instructions);
            }
        }
        plan.setSlides(slides);
        plan.setSlideCount(slides.size());
    }
}
