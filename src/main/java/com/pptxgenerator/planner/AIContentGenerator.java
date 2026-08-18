package com.pptxgenerator.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptxgenerator.client.GenerativeAiGateway;
import com.pptxgenerator.client.builder.GenerativeAiRequestBuilder;
import com.pptxgenerator.client.dto.JsonSchemaDto;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import com.pptxgenerator.model.ContentMap;
import com.pptxgenerator.model.EnrichedPlan;
import com.pptxgenerator.model.EnrichedSlide;
import com.pptxgenerator.model.SlideContent;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.ZoneContent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@ApplicationScoped
public class AIContentGenerator {

    // Keep concurrency bounded; the gateway also enforces a global request interval.
    // Groq free/on-demand limits are token based; serialize slide calls to avoid 429 bursts.
    private static final Executor CONTENT_EXECUTOR = Executors.newSingleThreadExecutor();

    @Inject
    public GenerativeAiGateway aiGateway;

    @Inject
    public AiResponseParser responseParser;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContentMap generateContent(EnrichedPlan plan, String topic) {
        return generateContent(plan, topic, null);
    }

    public ContentMap generateContent(EnrichedPlan plan, String topic, com.pptxgenerator.model.Theme theme) {
        List<CompletableFuture<SlideContent>> futures = IntStream.range(0, plan.getSlides().size())
            .mapToObj(index -> CompletableFuture.supplyAsync(
                () -> generateSlideContent(plan, index, topic, theme), CONTENT_EXECUTOR))
            .toList();

        ContentMap result = new ContentMap(plan.getTitle());
        for (int index = 0; index < futures.size(); index++) {
            result.addSlideContent("slide_" + index, futures.get(index).join());
        }
        return result;
    }

    private SlideContent generateSlideContent(EnrichedPlan plan, int index, String topic,
                                              com.pptxgenerator.model.Theme theme) {
        EnrichedSlide slide = plan.getSlides().get(index);
        String slideId = "slide_" + index;
        try {
            String prompt = buildUserPrompt(plan, index, topic, theme);
            TextRequestDto request = GenerativeAiRequestBuilder.builder()
                .systemPrompt(buildSystemPrompt())
                .userPrompt(prompt)
                .outputSchema(buildZoneSchema(slide))
                .temperature(0.4)
                .maxTokens(900)
                .build().toRequest();
            TextResponseDto response = aiGateway.processRequest(request);
            JsonNode json = responseParser.parseAsJsonNode(response.getCandidates().get(0).getText());
            return ensureUsableContent(toSlideContent(slideId, slide, json), slide);
        } catch (Exception e) {
            log.warn("Content generation failed for {}: {}", slideId, e.getMessage());
            return fallbackContent(slideId, slide);
        }
    }

    private String buildSystemPrompt() {
        return """
            Tu es un expert en rédaction de présentations professionnelles.
            Ta mission est de rédiger le CONTENU EXACT d'une slide PowerPoint.

            RÈGLES:
            - title, subtitle, center_title: maximum 8 mots, clair et impactant.
            - word: 1 à 3 caractères, chiffre, lettre ou expression courte.
            - line: une seule ligne, maximum 10 mots, style télégraphique.
            - body: texte libre, listes autorisées, densité adaptée à la surface.
            - picture, background, unknown_x: chaîne vide.
            - Ton professionnel et factuel, sans markdown.
            - Utilise toutes les données du detailed_context.
            - Les clés doivent être exactement zone_type_zone_id et les valeurs des strings.
            Réponds uniquement avec un JSON valide.
            """;
    }

    private String buildUserPrompt(EnrichedPlan plan, int index, String topic,
                                   com.pptxgenerator.model.Theme theme) {
        EnrichedSlide slide = plan.getSlides().get(index);
        String zones = slide.getAssignedLayout().getZones().stream()
            .filter(this::isContentZone)
            .map(zone -> describeZone(zone, theme))
            .collect(Collectors.joining("\n"));
        String previous = index > 0 ? plan.getSlides().get(index - 1).getPurpose() : "Aucun";
        String next = index + 1 < plan.getSlides().size() ? plan.getSlides().get(index + 1).getPurpose() : "Aucun";
        return String.format("""
            CONTEXTE DE LA PRÉSENTATION:
            - Titre global: %s
            - Slide précédente: %s
            - Purpose suivant: %s

            INFORMATIONS SUR CETTE SLIDE:
            - Numéro: %s
            - Type: %s
            - Purpose: %s
            - Content brief: %s

            CONTEXTE DÉTAILLÉ (UTILISER TOUTES CES DONNÉES):
            %s

            ZONES À REMPLIR:
            %s

            Génère maintenant le contenu exact pour chaque zone au format JSON.
            """, plan.getTitle(), previous, next, slide.getSlideNumber(), slide.getSlideType(),
            slide.getPurpose(), slide.getContentBrief(), slide.getDetailedContext(), zones);
    }

    private String describeZone(Zone zone, com.pptxgenerator.model.Theme theme) {
        return String.format("- %s_%d: type=%s, position=%s, surface=%.1f%%, taille=%dx%d EMU, expected=%s",
            zone.getZoneType(), zone.getZoneId(), zone.getZoneType(), zone.getPosition(),
            zone.getSurfacePercentage(), zone.getWidthEmu(), zone.getHeightEmu(),
            zone.getExpectedContentTypes());
    }

    private JsonSchemaDto buildZoneSchema(EnrichedSlide slide) {
        LinkedHashMap<String, JsonSchemaDto> properties = new LinkedHashMap<>();
        for (Zone zone : slide.getAssignedLayout().getZones()) {
            if (!isContentZone(zone)) continue;
            properties.put(zoneKey(slide.getAssignedLayout().getZones(), zone),
                JsonSchemaDto.builder().type(JsonSchemaDto.TypeEnum.STRING).build());
        }
        return JsonSchemaDto.builder()
            .type(JsonSchemaDto.TypeEnum.OBJECT)
            .properties(properties)
            .required(new ArrayList<>(properties.keySet()))
            .build();
    }

    private SlideContent toSlideContent(String slideId, EnrichedSlide slide, JsonNode json) {
        List<ZoneContent> contents = new ArrayList<>();
        for (Zone zone : slide.getAssignedLayout().getZones()) {
            if (!isContentZone(zone)) continue;
            String key = zoneKey(slide.getAssignedLayout().getZones(), zone);
            ZoneContent content = new ZoneContent();
            content.setZoneId(zone.getZoneId());
            content.setZoneType(zone.getZoneType());
            content.setZoneKey(key);
            JsonNode value = json.path(key);
            if ("picture".equals(zone.getZoneType()) && value.isArray()) {
                content.setImageDescription(value.size() == 0 ? null : value.get(value.size() - 1).asText());
                content.setContent("");
            } else {
                content.setContent(normalizeZoneValue(value, zone.getZoneType()));
            }
            contents.add(content);
        }
        SlideContent result = new SlideContent();
        result.setSlideId(slideId);
        result.setSlideTitle(slide.getPurpose() != null ? slide.getPurpose() : slide.getTitle());
        result.setZoneContents(contents);
        return result;
    }

    private String normalizeZoneValue(JsonNode value, String zoneType) {
        if (value == null || value.isMissingNode() || value.isNull()) return "";
        if (value.isTextual()) return value.asText();
        if (value.isArray()) {
            String separator = "title".equals(zoneType) || "center_title".equals(zoneType)
                ? " — " : "\n- ";
            String prefix = separator.startsWith("\n") ? "- " : "";
            return java.util.stream.StreamSupport.stream(value.spliterator(), false)
                .map(JsonNode::asText).collect(Collectors.joining(separator, prefix, ""));
        }
        return value.toString();
    }

    private String zoneKey(List<Zone> zones, Zone zone) {
        if (!"body".equals(zone.getZoneType())) {
            return zone.getZoneType() + "_" + zone.getZoneId();
        }
        List<Zone> bodies = zones.stream().filter(z -> "body".equals(z.getZoneType()))
            .sorted(java.util.Comparator.comparingLong(Zone::getXEmu)
                .thenComparingLong(Zone::getYEmu)).toList();
        return bodies.size() == 1 ? "body" : "body_" + (bodies.indexOf(zone) + 1);
    }

    private boolean isContentZone(Zone zone) {
        if ("footer".equals(zone.getZoneType())) return false;
        String placeholder = zone.getPlaceholderType();
        return !"dt".equals(placeholder) && !"ftr".equals(placeholder) && !"sldNum".equals(placeholder);
    }

    private SlideContent fallbackContent(String slideId, EnrichedSlide slide) {
        SlideContent result = new SlideContent();
        result.setSlideId(slideId);
        result.setSlideTitle(slide.getPurpose() != null ? slide.getPurpose() : slide.getTitle());
        List<ZoneContent> zones = new ArrayList<>();
        if (slide.getAssignedLayout() != null) {
            for (Zone zone : slide.getAssignedLayout().getZones()) {
                ZoneContent content = new ZoneContent();
                content.setZoneId(zone.getZoneId());
                content.setZoneType(zone.getZoneType());
                content.setZoneKey(zoneKey(slide.getAssignedLayout().getZones(), zone));
                if ("title".equals(zone.getZoneType()) || "center_title".equals(zone.getZoneType())) {
                    content.setContent(slide.getTitle() != null ? slide.getTitle() : slide.getContentBrief());
                } else if ("body".equals(zone.getZoneType()) && slide.getBulletPoints() != null) {
                    content.setContent("- " + String.join("\n- ", slide.getBulletPoints()));
                } else if ("picture".equals(zone.getZoneType())) {
                    content.setImageDescription("Image illustrative");
                } else {
                    content.setContent("");
                }
                zones.add(content);
            }
        } else {
            ZoneContent title = new ZoneContent();
            title.setZoneId(0);
            title.setZoneType("title");
            title.setContent(slide.getTitle() != null ? slide.getTitle() : slide.getContentBrief());
            zones.add(title);
            if (slide.getBulletPoints() != null && !slide.getBulletPoints().isEmpty()) {
                ZoneContent body = new ZoneContent();
                body.setZoneId(1);
                body.setZoneType("body");
                body.setContent("- " + String.join("\n- ", slide.getBulletPoints()));
                zones.add(body);
            }
        }
        result.setZoneContents(zones);
        return result;
    }

    private SlideContent ensureUsableContent(SlideContent content, EnrichedSlide slide) {
        String title = slide.getTitle() != null ? slide.getTitle() : slide.getContentBrief();
        String subtitle = slide.getPurpose();
        String context = slide.getDetailedContext() != null ? slide.getDetailedContext()
            : slide.getContentBrief();
        boolean bodyFilled = false;

        for (ZoneContent zone : content.getZoneContents()) {
            if (!isBlank(zone.getContent())) continue;
            if ("title".equals(zone.getZoneType()) || "center_title".equals(zone.getZoneType())) {
                zone.setContent(title == null ? "" : title);
            } else if ("subtitle".equals(zone.getZoneType())) {
                zone.setContent(subtitle == null ? "" : subtitle);
            } else if ("body".equals(zone.getZoneType()) && !bodyFilled) {
                zone.setContent(context == null ? "" : context);
                bodyFilled = true;
            }
        }
        return content;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
