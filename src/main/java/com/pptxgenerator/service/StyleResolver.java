package com.pptxgenerator.service;

import com.pptxgenerator.model.EffectiveZoneStyle;
import com.pptxgenerator.model.FontStyle;
import com.pptxgenerator.model.Margins;
import com.pptxgenerator.model.Theme;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.ZoneStyle;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class StyleResolver {
    private static final Margins DEFAULT_MARGINS = new Margins(127000, 127000, 76200, 76200);

    public EffectiveZoneStyle resolve(Zone zone, Theme theme) {
        ZoneStyle explicit = zone.getStyle();
        FontStyle themeFont = themeFont(zone, theme);
        int size = explicit != null && explicit.getFontSizePt() > 0 ? explicit.getFontSizePt()
            : themeFont != null && themeFont.getSizePt() > 0 ? themeFont.getSizePt() : 18;
        String family = explicit != null && explicit.getFontFamily() != null ? explicit.getFontFamily()
            : themeFont == null ? null : themeFont.getFamily();
        String weight = explicit != null && explicit.getFontWeight() != null ? explicit.getFontWeight()
            : themeFont == null ? "Regular" : themeFont.getWeight();
        String color = explicit != null && explicit.getColor() != null ? explicit.getColor() : null;
        String alignment = explicit != null && explicit.getAlignment() != null ? explicit.getAlignment() : "LEFT";
        Margins margins = zone.getMargins() == null ? DEFAULT_MARGINS : zone.getMargins();
        return EffectiveZoneStyle.builder().fontFamily(family).fontWeight(weight).fontSizePt(size)
            .color(color).alignment(alignment).margins(margins).build();
    }

    private FontStyle themeFont(Zone zone, Theme theme) {
        if (theme == null || theme.getFonts() == null) return null;
        return switch (zone.getZoneType()) {
            case "title", "center_title" -> theme.getFonts().getTitle();
            case "subtitle" -> theme.getFonts().getSubtitle();
            case "footer" -> theme.getFonts().getCaption();
            default -> theme.getFonts().getBody();
        };
    }
}
