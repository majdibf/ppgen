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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generates the text content of a single slide. Handles three cases:
 * <ul>
 *   <li>OUTLINE slides → deterministic (section titles extracted from transitions)</li>
 *   <li>SECTION_TRANSITION slides → deterministic (section title + number)</li>
 *   <li>All other slides → AI-generated via the content prompt builder</li>
 * </ul>
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SlideContentGenerator {

    private final AiCallExecutor aiCallExecutor;
    private final ContentPromptBuilder promptBuilder;

    public SlideContent generate(SlidePlanWithLayout slide,
                                 int slideIndex,
                                 List<SlidePlanWithLayout> allSlides,
                                 String modelId,
                                 String language,
                                 String tone,
                                 boolean webSearch) {
        SlideType slideType = slide.getSlideType();

        if (slideType == SlideType.OUTLINE) {
            return generateOutline(slide, allSlides);
        }

        if (slideType == SlideType.SECTION_TRANSITION) {
            int sectionNumber = calculateSectionNumber(slideIndex, allSlides);
            return generateSectionTransition(slide, sectionNumber);
        }

        return generateAiSlide(slide, slideIndex, allSlides, modelId, language, tone, webSearch);
    }

    private SlideContent generateOutline(SlidePlanWithLayout slide,
                                         List<SlidePlanWithLayout> allSlides) {
        log.debug("Generating deterministic OUTLINE content for slide {}", slide.getSlideNumber());

        List<String> sections = allSlides.stream()
                .filter(s -> s.getSlideType() == SlideType.SECTION_TRANSITION)
                .sorted(Comparator.comparingInt(SlidePlanWithLayout::getSlideNumber))
                .map(s -> Optional.ofNullable(s.getSectionTitle())
                        .filter(str -> !str.isBlank())
                        .orElseGet(() -> Optional.ofNullable(s.getContentBrief())
                                .filter(str -> !str.isBlank())
                                .orElseGet(() -> s.getPurpose())))
                .toList();

        List<Zone> zones = slide.getLayout().getZones();
        int sectionCount = sections.size();

        List<Zone> titleZones = zones.stream()
                .filter(z -> z.getZoneType() == ZoneType.TITLE || z.getZoneType() == ZoneType.CENTER_TITLE)
                .toList();
        List<Zone> lineZones = zones.stream()
                .filter(z -> z.getZoneType() == ZoneType.LINE)
                .sorted(Comparator.comparingInt(Zone::getZoneId))
                .toList();
        List<Zone> wordZones = zones.stream()
                .filter(z -> z.getZoneType() == ZoneType.WORD)
                .sorted(Comparator.comparingInt(Zone::getZoneId))
                .toList();

        Map<String, String> content = new HashMap<>();

        if (!titleZones.isEmpty()) {
            content.put(ZoneKeys.key(titleZones.get(0)), "Outline");
        }

        for (int i = 0; i < lineZones.size(); i++) {
            content.put(ZoneKeys.key(lineZones.get(i)), i < sectionCount ? sections.get(i) : "");
        }
        for (int i = 0; i < wordZones.size(); i++) {
            content.put(ZoneKeys.key(wordZones.get(i)), i < sectionCount ? String.format("%02d", i + 1) : "");
        }

        fillPictureAndBackgroundZones(zones, content);

        SlideContent slideContent = new SlideContent();
        slideContent.setContent(content);
        return slideContent;
    }

    private SlideContent generateSectionTransition(SlidePlanWithLayout slide, int sectionNumber) {
        log.debug("Generating deterministic SECTION_TRANSITION content for slide {}", slide.getSlideNumber());

        String sectionTitle = Optional.ofNullable(slide.getSectionTitle())
                .filter(str -> !str.isBlank())
                .or(() -> Optional.ofNullable(slide.getContentBrief()).filter(str -> !str.isBlank()))
                .orElse("");

        List<Zone> zones = slide.getLayout().getZones();

        List<Zone> titleZones = zones.stream()
                .filter(z -> z.getZoneType() == ZoneType.TITLE || z.getZoneType() == ZoneType.CENTER_TITLE)
                .toList();
        List<Zone> wordZones = zones.stream()
                .filter(z -> z.getZoneType() == ZoneType.WORD)
                .sorted(Comparator.comparingInt(Zone::getZoneId))
                .toList();
        List<Zone> lineZones = zones.stream()
                .filter(z -> z.getZoneType() == ZoneType.LINE)
                .sorted(Comparator.comparingInt(Zone::getZoneId))
                .toList();
        List<Zone> subtitleZones = zones.stream()
                .filter(z -> z.getZoneType() == ZoneType.SUBTITLE)
                .toList();

        Map<String, String> content = new HashMap<>();

        if (!titleZones.isEmpty()) {
            content.put(ZoneKeys.key(titleZones.get(0)), sectionTitle);
        }

        for (int i = 0; i < wordZones.size(); i++) {
            content.put(ZoneKeys.key(wordZones.get(i)), i == 0 ? String.format("%02d", sectionNumber) : "");
        }

        String hook = "";
        if (slide.getDetailedContext() != null && !slide.getDetailedContext().isBlank()) {
            String rawContext = slide.getDetailedContext();
            hook = rawContext.substring(0, Math.min(rawContext.length(), 100));
        }

        if (!subtitleZones.isEmpty()) {
            content.put(ZoneKeys.key(subtitleZones.get(0)), hook);
        } else {
            for (int i = 0; i < lineZones.size(); i++) {
                content.put(ZoneKeys.key(lineZones.get(i)), i == 0 ? hook : "");
            }
        }

        fillPictureAndBackgroundZones(zones, content);

        SlideContent slideContent = new SlideContent();
        slideContent.setContent(content);
        return slideContent;
    }

    private SlideContent generateAiSlide(SlidePlanWithLayout slide,
                                         int slideIndex,
                                         List<SlidePlanWithLayout> allSlides,
                                         String modelId,
                                         String language,
                                         String tone,
                                         boolean webSearch) {
        List<Zone> layoutZones = slide.getLayout().getZones();
        if (layoutZones == null || layoutZones.isEmpty()) {
            log.warn("Slide {} contains no layout zones. Defaulting to fallback", slide.getSlideNumber());
            return createFallbackContent(slide);
        }

        String prevTitle = slideIndex > 0 ? allSlides.get(slideIndex - 1).getPurpose() : null;
        String nextPurpose = slideIndex < allSlides.size() - 1 ? allSlides.get(slideIndex + 1).getPurpose() : null;

        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(
                slide, prevTitle, nextPurpose, language, tone, webSearch, layoutZones);

        return aiCallExecutor.call(
                modelId, systemPrompt, userPrompt,
                OutputSchemaProvider.createSlideContentSchema(layoutZones), SlideContent.class);    }

    private SlideContent createFallbackContent(SlidePlanWithLayout slide) {
        SlideContent content = new SlideContent();
        Map<String, String> map = new HashMap<>();
        if (slide != null && slide.getLayout() != null && slide.getLayout().getZones() != null) {
            for (Zone zone : slide.getLayout().getZones()) {
                map.put(ZoneKeys.key(zone), "Content to be generated");
            }
        }
        content.setContent(map);
        return content;
    }

    private void fillPictureAndBackgroundZones(List<Zone> zones, Map<String, String> content) {
        for (Zone zone : zones) {
            String zoneKey = ZoneKeys.key(zone);
            if (!content.containsKey(zoneKey)
                    && (zone.getZoneType() == ZoneType.PICTURE || zone.getZoneType() == ZoneType.BACKGROUND)) {
                content.put(zoneKey, "");
            }
        }
    }

    private int calculateSectionNumber(int slideIndex, List<SlidePlanWithLayout> slides) {
        return (int) slides.subList(0, slideIndex).stream()
                .filter(s -> s.getSlideType() == SlideType.SECTION_TRANSITION)
                .count() + 1;
    }
}
