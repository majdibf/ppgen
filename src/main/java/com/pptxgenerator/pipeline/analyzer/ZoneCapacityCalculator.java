package com.pptxgenerator.pipeline.analyzer;

import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ZoneType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * Estimates how many characters can physically fit inside a PowerPoint slide zone,
 * using the zone's real geometry (EMU) and the theme's body font size.
 *
 * <p>Java port of the client {@code ZoneCapacityCalculator}: EMU → pixels, minus the
 * internal text-frame padding, then estimate characters per line and available lines
 * before applying a 15% safety margin for wide characters.
 */
@Slf4j
@ApplicationScoped
public class ZoneCapacityCalculator {

    private static final double SCREEN_DPI = 96.0;
    private static final double POINTS_PER_INCH = 72.0;
    private static final double EMU_PER_INCH = 914400.0;
    private static final double POINTS_TO_PIXELS = SCREEN_DPI / POINTS_PER_INCH;

    private static final double CHAR_WIDTH_RATIO = 0.6;
    private static final double LINE_HEIGHT_RATIO = 1.2;
    private static final int MARGIN_PADDING_PX = 20;
    private static final double SAFETY_MARGIN = 0.85;

    private static final int DEFAULT_FONT_SIZE = 14;
    private static final int MIN_CHARACTERS_RETURNED = 1;

    private static final Set<ZoneType> SUPPORTED_TYPES = Set.of(
        ZoneType.LINE,
        ZoneType.WORD,
        ZoneType.BODY,
        ZoneType.TITLE
    );

    /**
     * Estimates the maximum number of characters that can fit into the given zone.
     *
     * @return Max characters (minimum 1), or null if the zone type is not supported.
     */
    public Integer calculateMaxCharacters(Zone zone, int fontSize) {
        if (zone == null || !SUPPORTED_TYPES.contains(zone.getZoneType())) {
            return null;
        }

        int safeFontSize = Math.max(1, fontSize);

        int maxChars = getMaxChars(zone, safeFontSize);

        log.debug("Zone {} {} {} -> {} chars", zone.getZoneId(), zone.getZoneType(), safeFontSize, maxChars);
        return Math.max(MIN_CHARACTERS_RETURNED, maxChars);
    }

    private int getMaxChars(Zone zone, int fontSize) {
        double fontSizePx = fontSize * POINTS_TO_PIXELS;

        long usableWidth = Math.max(0, emuToPixels(zone.getWidth()) - (2 * MARGIN_PADDING_PX));
        long usableHeight = Math.max(0, emuToPixels(zone.getHeight()) - (2 * MARGIN_PADDING_PX));

        double estimatedCharWidth = fontSizePx * CHAR_WIDTH_RATIO;
        int charsPerLine = (int) (usableWidth / estimatedCharWidth);

        int numLines = (zone.getZoneType() == ZoneType.BODY)
            ? (int) (usableHeight / (fontSizePx * LINE_HEIGHT_RATIO))
            : 1;

        return (int) ((charsPerLine * numLines) * SAFETY_MARGIN);
    }

    /**
     * Appends the capacity hint to a zone description.
     */
    public String enrichDescription(String description, Integer maxChars, ZoneType zoneType) {
        if (maxChars == null) {
            return description;
        }

        String hint = " [Max " + maxChars + " characters"
            + (zoneType == ZoneType.BODY ? " total" : "") + "]";
        return (description == null ? "" : description) + hint;
    }

    private long emuToPixels(long emu) {
        if (emu == 0) {
            return 0;
        }

        return (long) ((emu / EMU_PER_INCH) * SCREEN_DPI);
    }
}