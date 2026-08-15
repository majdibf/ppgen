package com.pptxgenerator.planner;

import com.pptxgenerator.client.GenerativeAiGateway;
import com.pptxgenerator.client.builder.GenerativeAiRequestBuilder;
import com.pptxgenerator.client.dto.JsonSchemaDto;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import com.pptxgenerator.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class AIContentGenerator {

    @Inject
    public GenerativeAiGateway aiGateway;

    @Inject
    public AiResponseParser responseParser;

    public ContentMap generateContent(EnrichedPlan enrichedPlan, String topic) {
        log.info("Generating content for presentation: {}", enrichedPlan.getTitle());
        
        ContentMap contentMap = new ContentMap(enrichedPlan.getTitle());
        
        for (int i = 0; i < enrichedPlan.getSlides().size(); i++) {
            EnrichedSlide slide = enrichedPlan.getSlides().get(i);
            String slideId = "slide_" + i;
            
            log.debug("Generating content for slide {}: {}", i, slide.getTitle());
            
            SlideContent slideContent = generateSlideContent(slide, slideId, topic);
            contentMap.addSlideContent(slideId, slideContent);
        }
        
        log.info("Content generation complete: {} slides", contentMap.getTotalSlides());
        return contentMap;
    }

    private SlideContent generateSlideContent(EnrichedSlide slide, String slideId, String topic) {
        SlideContent slideContent = new SlideContent();
        slideContent.setSlideId(slideId);
        slideContent.setSlideTitle(slide.getTitle());
        
        if (slide.getAssignedLayout() == null) {
            log.warn("Slide {} has no layout assigned, generating default content", slideId);
            slideContent.setZoneContents(generateDefaultContent(slide));
            return slideContent;
        }
        
        List<ZoneContent> zoneContents = new ArrayList<>();
        List<Zone> zones = slide.getAssignedLayout().getZones();
        
        if (zones != null) {
            for (Zone zone : zones) {
                ZoneContent zoneContent = generateZoneContent(zone, slide, topic);
                zoneContents.add(zoneContent);
            }
        }
        
        slideContent.setZoneContents(zoneContents);
        return slideContent;
    }

    private ZoneContent generateZoneContent(Zone zone, EnrichedSlide slide, String topic) {
        ZoneContent zoneContent = new ZoneContent();
        zoneContent.setZoneId(zone.getZoneId());
        zoneContent.setZoneType(zone.getZoneType());
        
        String zoneType = zone.getZoneType();
        
        if ("title".equals(zoneType) || "center_title".equals(zoneType)) {
            zoneContent.setContent(slide.getTitle());
        } else if ("subtitle".equals(zoneType)) {
            zoneContent.setContent(slide.getSubtitle() != null ? slide.getSubtitle() : "");
        } else if ("body".equals(zoneType)) {
            if (slide.getBulletPoints() != null && !slide.getBulletPoints().isEmpty()) {
                String content = String.join("\n• ", slide.getBulletPoints());
                zoneContent.setContent("• " + content);
            } else {
                zoneContent.setContent(generateBodyContentViaAI(zone, slide, topic));
            }
        } else if ("picture".equals(zoneType)) {
            zoneContent.setImageDescription(generateImageDescriptionViaAI(zone, slide, topic));
        } else if ("table".equals(zoneType)) {
            zoneContent.setTableData(generateTableDataViaAI(zone, slide, topic));
        } else if ("chart".equals(zoneType)) {
            zoneContent.setChartData(generateChartDataViaAI(zone, slide, topic));
        } else if ("footer".equals(zoneType)) {
            zoneContent.setContent("");
        } else {
            zoneContent.setContent("");
        }
        
        return zoneContent;
    }

    private String generateBodyContentViaAI(Zone zone, EnrichedSlide slide, String topic) {
        try {
            String systemPrompt = "Tu es un expert en rédaction de contenu pour présentations professionnelles.";
            String userPrompt = String.format(
                "Génère le contenu textuel pour une zone de type 'body' dans une slide de présentation.\n" +
                "Sujet: %s\n" +
                "Titre de la slide: %s\n" +
                "Type de slide: %s\n" +
                "Fournis un contenu concis et professionnel en 2-3 phrases maximum.",
                topic, slide.getTitle(), slide.getType()
            );
            
            TextRequestDto request = GenerativeAiRequestBuilder.builder()
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .temperature(0.7)
                .build()
                .toRequest();
            
            TextResponseDto response = aiGateway.processRequest(request);
            return response.getCandidates().get(0).getText();
        } catch (Exception e) {
            log.error("Error generating body content via AI", e);
            return "Contenu à venir";
        }
    }

    private String generateImageDescriptionViaAI(Zone zone, EnrichedSlide slide, String topic) {
        try {
            String systemPrompt = "Tu es un expert en description d'images pour présentations professionnelles.";
            String userPrompt = String.format(
                "Décris l'image idéale pour illustrer une slide de présentation.\n" +
                "Sujet: %s\n" +
                "Titre de la slide: %s\n" +
                "Type de slide: %s\n" +
                "Fournis une description concise et visuelle en 1-2 phrases.",
                topic, slide.getTitle(), slide.getType()
            );
            
            TextRequestDto request = GenerativeAiRequestBuilder.builder()
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .temperature(0.7)
                .build()
                .toRequest();
            
            TextResponseDto response = aiGateway.processRequest(request);
            return response.getCandidates().get(0).getText();
        } catch (Exception e) {
            log.error("Error generating image description via AI", e);
            return "Image illustrative";
        }
    }

    private List<List<String>> generateTableDataViaAI(Zone zone, EnrichedSlide slide, String topic) {
        try {
            String systemPrompt = "Tu es un expert en création de tableaux pour présentations professionnelles.";
            String userPrompt = String.format(
                "Génère un tableau JSON pour une slide de présentation.\n" +
                "Sujet: %s\n" +
                "Titre de la slide: %s\n" +
                "Retourne uniquement un tableau JSON 2D (liste de listes de strings) avec 3-4 lignes et 2-3 colonnes.",
                topic, slide.getTitle()
            );
            
            TextRequestDto request = GenerativeAiRequestBuilder.builder()
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .temperature(0.7)
                .build()
                .toRequest();
            
            TextResponseDto response = aiGateway.processRequest(request);
            String responseText = response.getCandidates().get(0).getText();
            
            return responseParser.parseAs(responseText, List.class);
        } catch (Exception e) {
            log.error("Error generating table data via AI", e);
            return List.of(
                List.of("Colonne 1", "Colonne 2"),
                List.of("Donnée 1", "Donnée 2"),
                List.of("Donnée 3", "Donnée 4")
            );
        }
    }

    private ChartData generateChartDataViaAI(Zone zone, EnrichedSlide slide, String topic) {
        try {
            String systemPrompt = "Tu es un expert en création de graphiques pour présentations professionnelles.";
            String userPrompt = String.format(
                "Génère les données d'un graphique pour une slide de présentation.\n" +
                "Sujet: %s\n" +
                "Titre de la slide: %s\n" +
                "Retourne un JSON avec: chart_type (bar/line/pie), title, categories (liste), series (liste avec name et values).",
                topic, slide.getTitle()
            );
            
            TextRequestDto request = GenerativeAiRequestBuilder.builder()
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .temperature(0.7)
                .build()
                .toRequest();
            
            TextResponseDto response = aiGateway.processRequest(request);
            String responseText = response.getCandidates().get(0).getText();
            
            return responseParser.parseAs(responseText, ChartData.class);
        } catch (Exception e) {
            log.error("Error generating chart data via AI", e);
            ChartData defaultChart = new ChartData();
            defaultChart.setChartType("bar");
            defaultChart.setTitle("Graphique");
            defaultChart.setCategories(List.of("Catégorie 1", "Catégorie 2", "Catégorie 3"));
            ChartData.Series series = new ChartData.Series();
            series.setName("Série 1");
            series.setValues(List.of(10.0, 20.0, 30.0));
            defaultChart.setSeries(List.of(series));
            return defaultChart;
        }
    }

    private List<ZoneContent> generateDefaultContent(EnrichedSlide slide) {
        List<ZoneContent> zoneContents = new ArrayList<>();
        
        ZoneContent titleContent = new ZoneContent();
        titleContent.setZoneId(0);
        titleContent.setZoneType("title");
        titleContent.setContent(slide.getTitle());
        zoneContents.add(titleContent);
        
        if (slide.getBulletPoints() != null && !slide.getBulletPoints().isEmpty()) {
            ZoneContent bodyContent = new ZoneContent();
            bodyContent.setZoneId(1);
            bodyContent.setZoneType("body");
            String content = String.join("\n• ", slide.getBulletPoints());
            bodyContent.setContent("• " + content);
            zoneContents.add(bodyContent);
        }
        
        return zoneContents;
    }
}
