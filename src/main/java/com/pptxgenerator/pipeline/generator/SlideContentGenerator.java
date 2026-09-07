package com.pptxgenerator.pipeline.generator;

import com.pptxgenerator.common.ai.AiCallExecutor;
import com.pptxgenerator.common.ai.OutputSchemaProvider;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.ZoneKeys;
import com.pptxgenerator.model.enums.ZoneType;
import com.pptxgenerator.pipeline.assigner.model.SlidePlanWithLayout;
import com.pptxgenerator.pipeline.generator.model.SlideContent;
import com.pptxgenerator.pipeline.planner.model.SlideType;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private final AiCallExecutor aiCallExecutor;
    private final ContentPromptBuilder promptBuilder;

    /**
     * Point d'entrée : génère le contenu d'une slide selon son type.
     */
    public SlideContent generate(
            SlidePlanWithLayout slide,
            int slideIndex,
            List<SlidePlanWithLayout> allSlides,
            String language,
            String tone,
            boolean webSearch) {

        return switch (slide.getSlideType()) {
            case OUTLINE -> generateOutlineContent(slide, allSlides);
            case SECTION_TRANSITION -> generateSectionTransitionContent(slide, allSlides);
            default -> generateWithAI(slide, slideIndex, allSlides, language, tone, webSearch);
        };
    }

    // ========================================================================
    // 1. GÉNÉRATION DÉTERMINISTE : SOMMAIRE (OUTLINE)
    // ========================================================================

    /**
     * Génère le contenu d'une slide de type OUTLINE (table des matières).
     *
     * <p>Le sommaire est adapté à la FORME du layout réellement attribué, qui varie
     * selon les templates (le POC ne couvrait que la forme canonique word+line):
     * <ul>
     *   <li><b>A - canonique</b> (word(s) + line(s)): sections dans les lines, numéros dans les words;</li>
     *   <li><b>B - body</b> (title + grande zone body): liste "01 - Section" dans le body;</li>
     *   <li><b>C - lines seules</b>: numéros intégrés au texte ("01 · Section") faute de zone dédiée.</li>
     * </ul>
     */
    private SlideContent generateOutlineContent(SlidePlanWithLayout slide, List<SlidePlanWithLayout> allSlides) {
        log.debug("Generating deterministic OUTLINE content for slide {}", slide.getSlideNumber());

        List<Zone> zones = slide.getLayout().getZones();
        List<String> sectionTitles = extractSectionTitles(allSlides);

        ZoneClassifier classifier = new ZoneClassifier(zones);

        Map<String, String> content = new HashMap<>();

        classifier.getFirstTitle()
                // POC parity: outline title = plan content brief, "Sommaire" as last resort
                .ifPresent(titleZone -> putContent(content, titleZone,
                        slide.getContentBrief() != null && !slide.getContentBrief().isBlank()
                                ? slide.getContentBrief() : "Sommaire"));

        boolean hasLines = !classifier.getLineZones().isEmpty();
        boolean hasWords = !classifier.getWordZones().isEmpty();
        List<Zone> bodyZones = zones.stream()
                .filter(zone -> zone.getZoneType() == ZoneType.BODY)
                .toList();

        if (hasLines && hasWords) {
            // Forme A - canonique (POC): sections -> lines, numéros -> words
            putMappedContent(content, classifier.getLineZones(), sectionTitles);
            putNumberedContent(content, classifier.getWordZones(), sectionTitles.size());
        } else if (!bodyZones.isEmpty()) {
            // Forme B - body: liste numérotée dans la zone body
            putContent(content, bodyZones.get(0), toNumberedLines(sectionTitles));
        } else if (hasLines) {
            // Forme C - lines seules: numéros intégrés au texte, pas de zone dédiée
            List<String> numbered = new ArrayList<>();
            for (int i = 0; i < sectionTitles.size(); i++) {
                numbered.add(prefixNumber(i + 1, sectionTitles.get(i)));
            }
            putMappedContent(content, classifier.getLineZones(), numbered);
        }
        // Forme D - rien d'exploitable: zones laissées vides

        classifier.getMediaZones()
                .forEach(zone -> putContent(content, zone, ""));

        return createSlideContent(content);
    }

    /** "- 01 - Une histoire de domination\n- 02 - ..." (liste à puces d'une zone body). */
    private String toNumberedLines(List<String> sectionTitles) {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < sectionTitles.size(); i++) {
            if (i > 0) {
                list.append('\n');
            }
            list.append("- ").append(String.format("%02d", i + 1)).append(" - ")
                    .append(sectionTitles.get(i));
        }
        return list.toString();
    }

    /** "01 · Une histoire de domination" quand le numéro n'a pas de zone dédiée. */
    private String prefixNumber(int number, String title) {
        return String.format("%02d", number) + " · " + title;
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

        ZoneClassifier classifier = new ZoneClassifier(zones);

        Map<String, String> content = new HashMap<>();

        classifier.getFirstTitle()
                .ifPresent(titleZone -> putContent(content, titleZone, sectionTitle));

        putSectionNumber(content, classifier.getWordZones(), sectionNumber);

        String hook = extractHook(slide);
        if (classifier.hasSubtitle()) {
            classifier.getFirstSubtitle()
                    .ifPresent(subZone -> putContent(content, subZone, hook));
        } else {
            putHookInFirstLine(content, classifier.getLineZones(), hook);
        }

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
            String language,
            String tone,
            boolean webSearch) {

        List<Zone> layoutZones = slide.getLayout().getZones();
        if (layoutZones == null || layoutZones.isEmpty()) {
            log.warn("Slide {} has no layout zones. Using fallback.", slide.getSlideNumber());
            return createFallbackContent(slide);
        }

        try {
            String prevTitle = getPreviousSlideTitle(slideIndex, allSlides);
            String nextPurpose = getNextSlidePurpose(slideIndex, allSlides);

            String systemPrompt = promptBuilder.buildSystemPrompt();
            String userPrompt = promptBuilder.buildUserPrompt(
                    slide, prevTitle, nextPurpose, language, tone, webSearch, layoutZones
            );

            return aiCallExecutor.call(
                    null, systemPrompt, userPrompt,
                    OutputSchemaProvider.createSlideContentSchema(layoutZones), SlideContent.class);

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
                .sorted(java.util.Comparator.comparingInt(SlidePlanWithLayout::getSlideNumber))
                .map(this::resolveSectionTitle)
                .collect(Collectors.toList());
    }

    /**
     * Résout le titre d'une section avec fallbacks.
     * Priorité : sectionTitle → contentBrief → purpose
     */
    private String resolveSectionTitle(SlidePlanWithLayout slide) {
        return Optional.ofNullable(slide.getSectionTitle())
                .filter(str -> !str.isBlank())
                .or(() -> Optional.ofNullable(slide.getContentBrief())
                        .filter(str -> !str.isBlank()))
                .or(() -> Optional.ofNullable(slide.getPurpose())
                        .filter(str -> !str.isBlank()))
                .orElse("");
    }

    /**
     * Extrait une accroche du contexte: privilégie la phrase "Message clé" quand elle
     * existe, sinon la première phrase informative; bornée à 100 caractères.
     * Évite de démarrer par la métadonnée "Intitulé de section : ...".
     */
    private String extractHook(SlidePlanWithLayout slide) {
        String context = slide.getDetailedContext();
        if (context == null || context.isBlank()) {
            return "";
        }
        java.util.regex.Matcher key = java.util.regex.Pattern
                .compile("(?i)message clé[^:]*:\\s*(.+?)(?:\\.\\s|$)")
                .matcher(context);
        String hook = key.find() ? key.group(1) : firstSentence(context);
        hook = hook.trim();
        return hook.length() > 100 ? hook.substring(0, 97).trim() + "..." : hook;
    }

    private String firstSentence(String context) {
        int end = context.indexOf(". ");
        return end > 0 ? context.substring(0, end + 1) : context;
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
