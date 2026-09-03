package com.pptxgenerator.pipeline.generator;

import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.ZoneKeys;
import com.pptxgenerator.model.enums.SlideType;
import com.pptxgenerator.model.enums.ZoneType;
import com.pptxgenerator.pipeline.assigner.model.SlidePlanWithLayout;
import com.pptxgenerator.pipeline.generator.model.SlideContent;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Générateur de contenu pour les slides.
 * 
 * <p>3 modes de génération :
 * <ul>
 *   <li><b>OUTLINE</b> : génération déterministe (sommaire)</li>
 *   <li><b>SECTION_TRANSITION</b> : génération déterministe (transition)</li>
 *   <li><b>Autres</b> : génération IA (CONTENT, etc.)</li>
 * </ul>
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SlideContentGenerator {

    private final GenerativeAiService generativeAiService;
    private final AiResponseParser aiResponseParser;
    private final ContentPromptBuilder promptBuilder;

    /**
     * Point d'entrée : génère le contenu d'une slide selon son type.
     */
    public SlideContent generate(
            int slideIndex,
            List<SlidePlanWithLayout> allSlides,
            String modelId,
            String language,
            String tone,
            boolean webSearch) {

        SlidePlanWithLayout slide = allSlides.get(slideIndex);

        return switch (slide.getSlideType()) {
            case OUTLINE -> generateOutlineContent(slide, allSlides);
            case SECTION_TRANSITION -> generateSectionTransitionContent(slide, allSlides);
            default -> generateWithAI(slide, slideIndex, allSlides, modelId, language, tone, webSearch);
        };
    }

    // ========================================================================
    // 1. GÉNÉRATION DÉTERMINISTE : SOMMAIRE (OUTLINE)
    // ========================================================================

    /**
     * Génère le contenu d'une slide de type OUTLINE (table des matières).
     */
    private SlideContent generateOutlineContent(SlidePlanWithLayout slide, List<SlidePlanWithLayout> allSlides) {
        log.debug("Generating deterministic OUTLINE content for slide {}", slide.getSlideNumber());

        List<Zone> zones = slide.getLayout().getZones();
        List<String> sectionTitles = extractSectionTitles(allSlides);

        // 1. Classifier les zones par type
        ZoneClassifier classifier = new ZoneClassifier(zones);

        // 2. Construire le contenu
        Map<String, String> content = new HashMap<>();

        // Titre : toujours "Outline"
        classifier.getFirstTitle()
                .ifPresent(titleZone -> putContent(content, titleZone, "Outline"));

        // Lignes : titres des sections
        putMappedContent(content, classifier.getLineZones(), sectionTitles);

        // Mots : numéros de section (01, 02, 03...)
        putNumberedContent(content, classifier.getWordZones(), sectionTitles.size());

        // Images/BG : chaînes vides
        classifier.getMediaZones()
                .forEach(zone -> putContent(content, zone, ""));

        return createSlideContent(content);
    }

    // ========================================================================
    // 2. GÉNÉRATION DÉTERMINISTE : TRANSITION (SECTION_TRANSITION)
    // ========================================================================

    /**
     * Génère le contenu d'une slide de type SECTION_TRANSITION.
     */
    private SlideContent generateSectionTransitionContent(SlidePlanWithLayout slide, 
                                                          List<SlidePlanWithLayout> allSlides) {
        log.debug("Generating deterministic SECTION_TRANSITION content for slide {}", slide.getSlideNumber());

        List<Zone> zones = slide.getLayout().getZones();
        int sectionNumber = calculateSectionNumber(slide, allSlides);
        String sectionTitle = resolveSectionTitle(slide);

        // 1. Classifier les zones
        ZoneClassifier classifier = new ZoneClassifier(zones);

        // 2. Construire le contenu
        Map<String, String> content = new HashMap<>();

        // Titre : titre de la section
        classifier.getFirstTitle()
                .ifPresent(titleZone -> putContent(content, titleZone, sectionTitle));

        // Numéro de section (premier mot uniquement)
        putSectionNumber(content, classifier.getWordZones(), sectionNumber);

        // Sous-titre ou ligne : hook (extrait du contexte)
        String hook = extractHook(slide);
        if (classifier.hasSubtitle()) {
            classifier.getFirstSubtitle()
                    .ifPresent(subZone -> putContent(content, subZone, hook));
        } else {
            putHookInFirstLine(content, classifier.getLineZones(), hook);
        }

        // Images/BG : chaînes vides
        classifier.getMediaZones()
                .forEach(zone -> putContent(content, zone, ""));

        return createSlideContent(content);
    }

    // ========================================================================
    // 3. GÉNÉRATION IA
    // ========================================================================

    /**
     * Génère le contenu via l'IA pour les slides de type CONTENT, etc.
     */
    private SlideContent generateWithAI(
            SlidePlanWithLayout slide,
            int slideIndex,
            List<SlidePlanWithLayout> allSlides,
            String modelId,
            String language,
            String tone,
            boolean webSearch) {

        List<Zone> layoutZones = slide.getLayout().getZones();
        if (layoutZones == null || layoutZones.isEmpty()) {
            log.warn("Slide {} has no layout zones. Using fallback.", slide.getSlideNumber());
            return createFallbackContent(slide);
        }

        try {
            // 1. Construire les prompts
            String prevTitle = getPreviousSlideTitle(slideIndex, allSlides);
            String nextPurpose = getNextSlidePurpose(slideIndex, allSlides);

            String systemPrompt = promptBuilder.buildSystemPrompt();
            String userPrompt = promptBuilder.buildUserPrompt(
                    slide, prevTitle, nextPurpose, language, tone, webSearch, layoutZones
            );

            // 2. Appel IA
            TextRequestDto request = GenerativeAiRequestBuilder.builder()
                    .modelId(modelId)
                    .systemPrompt(systemPrompt)
                    .userPrompt(userPrompt)
                    .outputSchema(OutputSchemaProvider.createSlideContentSchema(layoutZones))
                    .build()
                    .toRequest();

            TextResponseDto response = generativeAiService.processRequestWithRetry(request);
            String rawText = ResponseParsingUtils.parseTextResponseTo(response);
            log.debug("Token usage for slide {}: {}", slide.getSlideNumber(), 
                    ResponseParsingUtils.parseTokenUsage(response));

            return aiResponseParser.parseAs(rawText, SlideContent.class);

        } catch (Exception e) {
            log.error("Failed to generate slide {}: {}", slide.getSlideNumber(), e.getMessage());
            return createFallbackContent(slide);
        }
    }

    // ========================================================================
    // MÉTHODES UTILITAIRES
    // ========================================================================

    /**
     * Extrait les titres des sections depuis les slides de transition.
     */
    private List<String> extractSectionTitles(List<SlidePlanWithLayout> allSlides) {
        return allSlides.stream()
                .filter(s -> s.getSlideType() == SlideType.SECTION_TRANSITION)
                .sorted(Comparator.comparingInt(SlidePlanWithLayout::getSlideNumber))
                .map(this::resolveSectionTitle)
                .collect(Collectors.toList());
    }

    /**
     * Résout le titre d'une section avec fallbacks.
     * Priorité : sectionTitle → contentBrief → purpose
     */
    private String resolveSectionTitle(SlidePlanWithLayout slide) {
        return Optional.ofNullable(slide.getSectionTitle())
                .filter(StringUtils::isNotBlank)
                .or(() -> Optional.ofNullable(slide.getContentBrief())
                        .filter(StringUtils::isNotBlank))
                .or(() -> Optional.ofNullable(slide.getPurpose())
                        .filter(StringUtils::isNotBlank))
                .orElse("");
    }

    /**
     * Extrait un hook du contexte (100 caractères max).
     */
    private String extractHook(SlidePlanWithLayout slide) {
        String context = slide.getDetailedContext();
        if (context == null || context.isBlank()) {
            return "";
        }
        return context.substring(0, Math.min(context.length(), 100));
    }

    /**
     * Calcule le numéro de section (1-based).
     */
    private int calculateSectionNumber(SlidePlanWithLayout slide, List<SlidePlanWithLayout> allSlides) {
        return (int) allSlides.stream()
                .filter(s -> s.getSlideType() == SlideType.SECTION_TRANSITION)
                .takeWhile(s -> s != slide)
                .count() + 1;
    }

    private String getPreviousSlideTitle(int index, List<SlidePlanWithLayout> allSlides) {
        return index > 0 ? allSlides.get(index - 1).getPurpose() : null;
    }

    private String getNextSlidePurpose(int index, List<SlidePlanWithLayout> allSlides) {
        return index < allSlides.size() - 1 ? allSlides.get(index + 1).getPurpose() : null;
    }

    // ========================================================================
    // HELPERS DE MAPPING
    // ========================================================================

    private void putContent(Map<String, String> content, Zone zone, String value) {
        content.put(ZoneKeys.key(zone), value);
    }

    private void putMappedContent(Map<String, String> content, List<Zone> zones, List<String> values) {
        for (int i = 0; i < zones.size() && i < values.size(); i++) {
            putContent(content, zones.get(i), values.get(i));
        }
        // Remplir les zones restantes avec chaîne vide
        for (int i = values.size(); i < zones.size(); i++) {
            putContent(content, zones.get(i), "");
        }
    }

    private void putNumberedContent(Map<String, String> content, List<Zone> zones, int count) {
        for (int i = 0; i < zones.size(); i++) {
            String value = i < count ? String.format("%02d", i + 1) : "";
            putContent(content, zones.get(i), value);
        }
    }

    private void putSectionNumber(Map<String, String> content, List<Zone> wordZones, int sectionNumber) {
        if (!wordZones.isEmpty()) {
            putContent(content, wordZones.get(0), String.format("%02d", sectionNumber));
            // Les autres mots restent vides
            for (int i = 1; i < wordZones.size(); i++) {
                putContent(content, wordZones.get(i), "");
            }
        }
    }

    private void putHookInFirstLine(Map<String, String> content, List<Zone> lineZones, String hook) {
        if (!lineZones.isEmpty()) {
            putContent(content, lineZones.get(0), hook);
            for (int i = 1; i < lineZones.size(); i++) {
                putContent(content, lineZones.get(i), "");
            }
        }
    }

    private SlideContent createSlideContent(Map<String, String> content) {
        SlideContent slideContent = new SlideContent();
        slideContent.setContent(content);
        return slideContent;
    }

    /**
     * Contenu de fallback quand la génération échoue.
     */
    private SlideContent createFallbackContent(SlidePlanWithLayout slide) {
        Map<String, String> content = new HashMap<>();
        if (slide != null && slide.getLayout() != null && slide.getLayout().getZones() != null) {
            for (Zone zone : slide.getLayout().getZones()) {
                content.put(ZoneKeys.key(zone), "Content to be generated");
            }
        }
        SlideContent slideContent = new SlideContent();
        slideContent.setContent(content);
        return slideContent;
    }
}