package com.pptxgenerator.analyzer;

import com.pptxgenerator.model.FontStyle;
import com.pptxgenerator.model.Theme;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.dml.BaseStyles;
import org.docx4j.dml.CTColor;
import org.docx4j.dml.CTColorScheme;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.ThemePart;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@ApplicationScoped
public class ThemeExtractor {

    /**
     * Extrait le thème complet (couleurs + polices) du template
     */
    public Theme extract(PresentationMLPackage pptx) {
        log.info("Extraction du thème...");

        Map<String, String> colors = extractColors(pptx);
        Map<String, FontStyle> fonts = extractFonts(pptx);

        return Theme.builder()
            .colors(colors)
            .fonts(fonts)
            .build();
    }

    private Map<String, String> extractColors(PresentationMLPackage pptx) {
        try {
            ThemePart themePart = pptx.getMainPresentationPart().getThemePart();

            if (themePart == null) {
                log.warn("Aucun thème trouvé, utilisation des valeurs par défaut");
                return getDefaultColors();
            }

            org.docx4j.dml.Theme theme = themePart.getContents();
            CTColorScheme colorScheme = theme.getThemeElements().getClrScheme();

            Map<String, String> colors = new LinkedHashMap<>();
            colors.put("primary", extractColorFromChoice(colorScheme.getAccent1()));
            colors.put("secondary", extractColorFromChoice(colorScheme.getAccent2()));
            colors.put("accent1", extractColorFromChoice(colorScheme.getAccent3()));
            colors.put("accent2", extractColorFromChoice(colorScheme.getAccent4()));
            colors.put("background", extractColorFromChoice(colorScheme.getLt1()));
            colors.put("text_primary", extractColorFromChoice(colorScheme.getDk1()));
            colors.put("text_secondary", extractColorFromChoice(colorScheme.getDk2()));

            return colors;

        } catch (Exception e) {
            log.error("Erreur extraction couleurs: {}", e.getMessage());
            return getDefaultColors();
        }
    }

    private String extractColorFromChoice(CTColor color) {
        if (color == null) return "#000000";

        // Priorité 1: srgbClr (couleur RGB explicite)
        if (color.getSrgbClr() != null) {
            return "#" + color.getSrgbClr().getVal().toUpperCase();
        }

        // Priorité 2: schemeClr (couleur du schéma)
        if (color.getSchemeClr() != null) {
            // Résoudre la couleur du schéma (simplifié)
            return "#000000"; // À implémenter selon la structure réelle
        }

        return "#000000";
    }

    private Map<String, FontStyle> extractFonts(PresentationMLPackage pptx) {
        try {
            ThemePart themePart = pptx.getMainPresentationPart().getThemePart();

            if (themePart == null) {
                return getDefaultFonts();
            }

            org.docx4j.dml.Theme theme = themePart.getContents();
            BaseStyles.FontScheme fontScheme = theme.getThemeElements().getFontScheme();

            String majorFont = fontScheme.getMajorFont().getLatin().getTypeface();
            String minorFont = fontScheme.getMinorFont().getLatin().getTypeface();

            Map<String, FontStyle> fonts = new LinkedHashMap<>();
            fonts.put("title", new FontStyle(majorFont, "Bold", 32));
            fonts.put("subtitle", new FontStyle(majorFont, "SemiBold", 24));
            fonts.put("body", new FontStyle(minorFont, "Regular", 14));
            fonts.put("caption", new FontStyle(minorFont, "Light", 10));

            return fonts;

        } catch (Exception e) {
            log.error("Erreur extraction polices: {}", e.getMessage());
            return getDefaultFonts();
        }
    }

    private Map<String, String> getDefaultColors() {
        Map<String, String> colors = new LinkedHashMap<>();
        colors.put("primary", "#1B2A4A");
        colors.put("secondary", "#E63946");
        colors.put("accent1", "#F4A261");
        colors.put("accent2", "#2A9D8F");
        colors.put("background", "#FFFFFF");
        colors.put("text_primary", "#1D1D1D");
        colors.put("text_secondary", "#6B7280");
        return colors;
    }

    private Map<String, FontStyle> getDefaultFonts() {
        Map<String, FontStyle> fonts = new LinkedHashMap<>();
        fonts.put("title", new FontStyle("Calibri", "Bold", 44));
        fonts.put("subtitle", new FontStyle("Calibri", "Regular", 32));
        fonts.put("body", new FontStyle("Calibri", "Regular", 18));
        fonts.put("caption", new FontStyle("Calibri", "Regular", 14));
        return fonts;
    }
}
