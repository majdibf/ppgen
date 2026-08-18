package com.pptxgenerator.analyzer;

import com.pptxgenerator.model.*;
import com.pptxgenerator.model.Theme;
import jakarta.enterprise.context.ApplicationScoped;
import org.docx4j.dml.*;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.PresentationML.MainPresentationPart;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.docx4j.openpackaging.parts.ThemePart;
import org.pptx4j.pml.*;

import java.io.File;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class TemplateAnalyzer {

    private static final long EMU_PER_INCH = 914400L;

    public TemplateStructure analyze(InputStream templateStream) throws Exception {
        PresentationMLPackage pptx = PresentationMLPackage.load(templateStream);
        return analyzePackage(pptx, "template.pptx");
    }

    public TemplateStructure analyze(String templatePath) throws Exception {
        PresentationMLPackage pptx = PresentationMLPackage.load(new File(templatePath));
        return analyzePackage(pptx, new File(templatePath).getName());
    }

    private TemplateStructure analyzePackage(PresentationMLPackage pptx, String templateName) throws Exception {
        TemplateStructure structure = new TemplateStructure();
        MainPresentationPart mainPart = pptx.getMainPresentationPart();

        structure.setSlideDimensions(extractSlideDimensions(mainPart));
        structure.setTheme(extractTheme(pptx));
        structure.setLayouts(extractLayouts(pptx, mainPart, structure.getTheme()));
        structure.setStructuralElements(buildStructuralElements(structure.getLayouts()));
        structure.setMetadata(buildMetadata(templateName, mainPart, structure.getLayouts().size()));

        return structure;
    }

    private SlideDimensions extractSlideDimensions(MainPresentationPart mainPart) throws Exception {
        Presentation presentation = mainPart.getContents();
        Presentation.SldSz sldSz = presentation.getSldSz();

        SlideDimensions dims = new SlideDimensions();
        int widthEmu = sldSz.getCx();
        int heightEmu = sldSz.getCy();

        dims.setWidth(widthEmu);
        dims.setHeight(heightEmu);
        dims.setWidthInches(Math.round(widthEmu * 1000.0 / EMU_PER_INCH) / 1000.0);
        dims.setHeightInches(Math.round(heightEmu * 1000.0 / EMU_PER_INCH) / 1000.0);
        dims.setUnit("EMU");

        return dims;
    }

    private Theme extractTheme(PresentationMLPackage pptx) throws Exception {
        ThemePart themePart = findThemePart(pptx);
        if (themePart == null) {
            return new Theme();
        }

        org.docx4j.dml.Theme dmlTheme = themePart.getContents();
        Theme result = new Theme();
        result.setColors(extractThemeColors(dmlTheme));
        result.setFonts(extractThemeFonts(dmlTheme));
        return result;
    }

    private ThemePart findThemePart(PresentationMLPackage pptx) {
        for (Part part : pptx.getParts().getParts().values()) {
            if (part instanceof ThemePart) {
                return (ThemePart) part;
            }
        }
        return null;
    }

    private ThemeColors extractThemeColors(org.docx4j.dml.Theme dmlTheme) {
        ThemeColors colors = new ThemeColors();

        if (dmlTheme.getThemeElements() != null && dmlTheme.getThemeElements().getClrScheme() != null) {
            CTColorScheme colorScheme = dmlTheme.getThemeElements().getClrScheme();
            colors.setPrimary(colorToHex(colorScheme.getAccent1()));
            colors.setAccent1(colorToHex(colorScheme.getAccent1()));
            colors.setSecondary(colorToHex(colorScheme.getAccent2()));
            colors.setAccent2(colorToHex(colorScheme.getAccent2()));
            colors.setBackground(colorToHex(colorScheme.getLt1()));
            colors.setTextPrimary(colorToHex(colorScheme.getDk1()));
            colors.setTextSecondary(colorToHex(colorScheme.getDk2()));
        }

        return colors;
    }

    private String colorToHex(CTColor color) {
        if (color == null) return "#000000";

        if (color.getSrgbClr() != null) {
            return "#" + new String(color.getSrgbClr().getVal()).toUpperCase();
        }
        if (color.getSysClr() != null) {
            byte[] lastClr = color.getSysClr().getLastClr();
            if (lastClr != null) {
                String hex = new String(lastClr);
                if (hex.startsWith("#")) {
                    return hex.toUpperCase();
                }
            }
        }

        return "#000000";
    }

    private ThemeFonts extractThemeFonts(org.docx4j.dml.Theme dmlTheme) {
        ThemeFonts fonts = new ThemeFonts();

        if (dmlTheme.getThemeElements() == null || dmlTheme.getThemeElements().getFontScheme() == null) {
            return fonts;
        }

        BaseStyles.FontScheme fontScheme = dmlTheme.getThemeElements().getFontScheme();
        FontCollection majorFont = fontScheme.getMajorFont();
        FontCollection minorFont = fontScheme.getMinorFont();

        String majorFamily = (majorFont != null && majorFont.getLatin() != null)
            ? majorFont.getLatin().getTypeface() : "Arial";
        String minorFamily = (minorFont != null && minorFont.getLatin() != null)
            ? minorFont.getLatin().getTypeface() : "Arial";

        FontStyle title = new FontStyle();
        title.setFamily(majorFamily);
        title.setWeight("Bold");
        title.setSizePt(42);
        title.setColorRef("primary");
        fonts.setTitle(title);

        FontStyle subtitle = new FontStyle();
        subtitle.setFamily(majorFamily);
        subtitle.setWeight("Regular");
        subtitle.setSizePt(32);
        subtitle.setColorRef("text_secondary");
        fonts.setSubtitle(subtitle);

        FontStyle body = new FontStyle();
        body.setFamily(minorFamily);
        body.setWeight("Regular");
        body.setSizePt(26);
        body.setColorRef("text_primary");
        fonts.setBody(body);

        FontStyle caption = new FontStyle();
        caption.setFamily(minorFamily);
        caption.setWeight("Regular");
        caption.setSizePt(20);
        caption.setColorRef("text_secondary");
        fonts.setCaption(caption);

        return fonts;
    }

    private List<SlideLayout> extractLayouts(PresentationMLPackage pptx, MainPresentationPart mainPart, Theme theme) throws Exception {
        List<SlideLayout> layouts = new ArrayList<>();
        int layoutIndex = 0;

        for (Part part : pptx.getParts().getParts().values()) {
            if (part instanceof SlideLayoutPart) {
                SlideLayoutPart layoutPart = (SlideLayoutPart) part;
                SlideLayout layout = analyzeLayout(layoutPart, layoutIndex, pptx, theme);
                layouts.add(layout);
                layoutIndex++;
            }
        }

        return layouts;
    }

    private SlideLayout analyzeLayout(SlideLayoutPart layoutPart, int index, PresentationMLPackage pptx, Theme theme) throws Exception {
        SlideLayout layout = new SlideLayout();
        layout.setLayoutId("layout_" + index);
        layout.setOriginalName(layoutPart.getPartName().toString());
        layout.setDescription("Layout détecté depuis le template PPTX");

        SldLayout sldLayout = layoutPart.getContents();

        List<Zone> zones = new ArrayList<>();
        int zoneId = 0;

        if (sldLayout.getCSld() != null && sldLayout.getCSld().getSpTree() != null) {
            for (Object sp : sldLayout.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame()) {
                if (sp instanceof Shape) {
                    Shape shape = (Shape) sp;
                    if (shape.getNvSpPr() != null && shape.getNvSpPr().getNvPr() != null
                            && shape.getNvSpPr().getNvPr().getPh() != null) {

                        Zone zone = analyzeZone(shape, zoneId, theme, layoutPart);
                        if (zone != null) {
                            zones.add(zone);
                            zoneId++;
                        }
                    }
                }
            }
        }

        SlideDimensions slideDims = extractSlideDimensions(pptx.getMainPresentationPart());
        double slideWidth = slideDims.getWidth();
        double slideHeight = slideDims.getHeight();

        assignReadingOrderAndPosition(zones, slideWidth, slideHeight);

        calculateSurfacePercentages(zones, pptx);
        calculateImportance(zones);

        layout.setZones(zones);
        layout.setSemanticType(determineSemanticType(zones));
        layout.setStructuralInfo(buildStructuralInfo(zones));

        String modelSlide = findModelSlide(pptx, layoutPart);
        if (modelSlide != null) {
            layout.setModelSlide(modelSlide);
        }

        return layout;
    }

    private Zone analyzeZone(Shape shape, int zoneId, Theme theme, SlideLayoutPart layoutPart) throws Exception {
        Zone zone = new Zone();
        zone.setZoneId(zoneId);

        // 1. EXTRAIRE LES DONNÉES BRUTES DU PLACEHOLDER
        CTPlaceholder ph = shape.getNvSpPr().getNvPr().getPh();
        String phType = (ph.getType() != null) ? ph.getType().value() : "body";
        int phIdx = (int) ph.getIdx();

        PlaceholderInfo placeholder = new PlaceholderInfo();
        placeholder.setType(phType);
        placeholder.setIdx(phIdx);
        placeholder.setName(shape.getNvSpPr().getCNvPr() != null
                ? shape.getNvSpPr().getCNvPr().getName() : "");
        placeholder.setHasText(hasText(shape));
        zone.setPlaceholder(placeholder);

        // 2. STOCKER LE TYPE EXACT
        zone.setPlaceholderType(phType);
        zone.setPlaceholderName(placeholder.getName());

        // 3. DÉTERMINER LE TYPE DE ZONE (SIMPLIFIÉ)
        String zoneType = mapPlaceholderType(phType);
        zone.setZoneType(zoneType);

        // 4. EXTRAIRE LES DIMENSIONS
        if (shape.getSpPr() != null && shape.getSpPr().getXfrm() != null) {
            CTTransform2D xfrm = shape.getSpPr().getXfrm();
            if (xfrm.getOff() != null) {
                zone.setXEmu(xfrm.getOff().getX());
                zone.setYEmu(xfrm.getOff().getY());
            }
            if (xfrm.getExt() != null) {
                zone.setWidthEmu(xfrm.getExt().getCx());
                zone.setHeightEmu(xfrm.getExt().getCy());
                zone.setWidthInches(Math.round(zone.getWidthEmu() * 1000.0 / EMU_PER_INCH) / 1000.0);
                zone.setHeightInches(Math.round(zone.getHeightEmu() * 1000.0 / EMU_PER_INCH) / 1000.0);
            }
        }

        // 5. EXTRAIRE LE STYLE
        Shape inheritedShape = findInheritedShape(layoutPart, phType, phIdx);
        ZoneStyle directStyle = extractZoneStyle(shape);
        if (inheritedShape != null && isEmptyStyle(directStyle)) {
            directStyle = extractZoneStyle(inheritedShape);
        }
        zone.setStyle(directStyle);

        // 6. EXTRAIRE LES MARGES
        Margins margins = extractMargins(shape);
        if (inheritedShape != null && isZeroMargins(margins)) {
            margins = extractMargins(inheritedShape);
        }
        zone.setMargins(margins);

        // 7. DÉTECTION DES BADGES ET RÔLES SÉMANTIQUES
        detectBadgeAndSemanticRole(zone);

        // 8. DÉTECTION DES TYPES DE CONTENU ATTENDUS
        zone.setExpectedContentTypes(detectExpectedContentTypes(zone));

        // 9. ESTIMATION DU NOMBRE MAX DE CARACTÈRES
        zone.setMaxChars(calculateMaxChars(zone, theme));
        zone.setDescription(buildZoneDescription(zone));

        // 10. DÉTECTION DES IMAGES
        if ("picture".equals(phType) || "pic".equals(phType)) {
            ImageInfo imgInfo = new ImageInfo();
            imgInfo.setName(placeholder.getName());
            imgInfo.setPlaceholder(true);
            zone.setImageInfo(imgInfo);
            zone.setImage(true);
        }

        // 11. DÉTECTION DU TYPE DE TEXTE
        zone.setTitle("title".equals(zoneType) || "center_title".equals(zoneType) || "subtitle".equals(zoneType));
        zone.setBody("body".equals(zoneType));
        zone.setFooter("footer".equals(zoneType));
        zone.setDate("dt".equals(phType));

        return zone;
    }

    private Shape findInheritedShape(SlideLayoutPart layoutPart, String type, int idx) throws Exception {
        if (layoutPart == null || layoutPart.getSlideMasterPart() == null) return null;
        SldMaster master = layoutPart.getSlideMasterPart().getContents();
        if (master == null || master.getCSld() == null || master.getCSld().getSpTree() == null) return null;
        for (Object value : master.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame()) {
            if (!(value instanceof Shape shape) || shape.getNvSpPr() == null
                || shape.getNvSpPr().getNvPr() == null || shape.getNvSpPr().getNvPr().getPh() == null) continue;
            CTPlaceholder placeholder = shape.getNvSpPr().getNvPr().getPh();
            if (placeholder.getIdx() == idx
                && (placeholder.getType() == null || type.equals(placeholder.getType().value()))) return shape;
        }
        return null;
    }

    private boolean isEmptyStyle(ZoneStyle style) {
        return style == null || (style.getFontFamily() == null && style.getFontSizePt() == 0
            && style.getColor() == null && style.getAlignment() == null);
    }

    private boolean isZeroMargins(Margins margins) {
        return margins == null || (margins.getLeft() == 0 && margins.getRight() == 0
            && margins.getTop() == 0 && margins.getBottom() == 0);
    }

    private boolean hasText(Shape shape) {
        if (shape.getTxBody() == null) return false;
        for (CTTextParagraph p : shape.getTxBody().getP()) {
            for (Object run : p.getEGTextRun()) {
                if (run instanceof CTRegularTextRun) {
                    return true;
                }
            }
        }
        return false;
    }

    private String mapPlaceholderType(String phType) {
        switch (phType) {
            case "title": return "title";
            case "ctrTitle": return "center_title";
            case "subTitle": return "subtitle";
            case "body": return "body";
            case "obj": return "body";
            case "pic": return "picture";
            case "ftr": return "footer";
            case "sldNum": return "footer";
            case "dt": return "footer";
            default: return "body";
        }
    }

    private ZoneStyle extractZoneStyle(Shape shape) {
        ZoneStyle style = new ZoneStyle();

        if (shape.getTxBody() == null) return style;

        CTTextBody txBody = shape.getTxBody();

        if (txBody.getLstStyle() != null && txBody.getLstStyle().getDefPPr() != null) {
            applyParagraphStyle(style, txBody.getLstStyle().getDefPPr());
        }

        if (!txBody.getP().isEmpty()) {
            CTTextParagraph p = txBody.getP().get(0);
            if (p.getPPr() != null) {
                applyParagraphStyle(style, p.getPPr());
            }
            for (Object run : p.getEGTextRun()) {
                if (run instanceof CTRegularTextRun) {
                    CTRegularTextRun textRun = (CTRegularTextRun) run;
                    if (textRun.getRPr() != null) {
                        applyRunStyle(style, textRun.getRPr());
                    }
                    break;
                }
            }
        }

        return style;
    }

    private void applyParagraphStyle(ZoneStyle style, CTTextParagraphProperties pPr) {
        if (pPr.getAlgn() != null) {
            style.setAlignment(pPr.getAlgn().value().toUpperCase());
        }
        if (pPr.getDefRPr() != null) {
            applyRunStyle(style, pPr.getDefRPr());
        }
    }

    private void applyRunStyle(ZoneStyle style, CTTextCharacterProperties rPr) {
        if (rPr.getSz() != null) {
            style.setFontSizePt(rPr.getSz() / 100);
        }

        if (rPr.isB() != null && rPr.isB()) {
            style.setFontWeight("Bold");
        } else if (style.getFontWeight() == null) {
            style.setFontWeight("Regular");
        }

        if (rPr.getLatin() != null && rPr.getLatin().getTypeface() != null) {
            style.setFontFamily(rPr.getLatin().getTypeface());
        }

        if (rPr.getSolidFill() != null && rPr.getSolidFill().getSrgbClr() != null) {
            style.setColor("#" + new String(rPr.getSolidFill().getSrgbClr().getVal()).toUpperCase());
        }
    }

    private void assignReadingOrderAndPosition(List<Zone> zones, double slideWidth, double slideHeight) {
        // 1. Trier par position (y puis x)
        zones.sort((a, b) -> {
            int yDiff = Long.compare(a.getYEmu(), b.getYEmu());
            if (yDiff != 0) return yDiff;
            return Long.compare(a.getXEmu(), b.getXEmu());
        });

        // 2. Assigner l'ordre de lecture
        for (int i = 0; i < zones.size(); i++) {
            Zone zone = zones.get(i);
            zone.setReadingOrder(i + 1);
            zone.setPosition(determinePosition(zone, slideWidth, slideHeight));
        }

        // 3. Détecter les zones liées (badge + titre)
        detectPairedZones(zones);
    }

    private void detectPairedZones(List<Zone> zones) {
        // Chercher les badges et les titres proches
        for (int i = 0; i < zones.size(); i++) {
            Zone current = zones.get(i);
            if (!current.isBadge() && !"section_number".equals(current.getSemanticRole())) continue;

            // Chercher un titre à proximité (même colonne, juste en dessous)
            for (int j = 0; j < zones.size(); j++) {
                if (i == j) continue;
                Zone other = zones.get(j);
                if (!other.isTitle() && !"main_title".equals(other.getSemanticRole())) continue;

                // Vérifier la proximité
                long xDiff = Math.abs(current.getXEmu() - other.getXEmu());
                long yDiff = other.getYEmu() - current.getYEmu();
                long currentHeight = current.getHeightEmu();

                // Même colonne (tolérance) et badge juste au-dessus du titre
                if (xDiff < current.getWidthEmu() && yDiff > 0 && yDiff < currentHeight * 3) {
                    current.setPairedWithZoneId(other.getZoneId());
                    other.setPairedWithZoneId(current.getZoneId());
                    break;
                }
            }
        }
    }

    private void calculateSurfacePercentages(List<Zone> zones, PresentationMLPackage pptx) throws Exception {
        MainPresentationPart mainPart = pptx.getMainPresentationPart();
        Presentation.SldSz sldSz = mainPart.getContents().getSldSz();
        double slideWidth = (double) sldSz.getCx();
        double slideHeight = (double) sldSz.getCy();
        double slideArea = slideWidth * slideHeight;

        for (Zone zone : zones) {
            double zoneArea = (double) zone.getWidthEmu() * zone.getHeightEmu();
            zone.setSurfacePercentage(Math.round(zoneArea * 1000.0 / slideArea) / 10.0);
        }
    }

    private void calculateImportance(List<Zone> zones) {
        for (Zone zone : zones) {
            String zoneType = zone.getZoneType();
            double surface = zone.getSurfacePercentage();

            if ("title".equals(zoneType) || "center_title".equals(zoneType)) {
                zone.setImportance("HIGH");
            } else if ("subtitle".equals(zoneType) || "picture".equals(zoneType)) {
                zone.setImportance(surface > 15 ? "HIGH" : "MEDIUM");
            } else if ("body".equals(zoneType)) {
                zone.setImportance(surface > 20 ? "HIGH" : surface > 10 ? "MEDIUM" : "LOW");
            } else if ("footer".equals(zoneType)) {
                zone.setImportance("LOW");
            } else {
                zone.setImportance(surface > 15 ? "MEDIUM" : "LOW");
            }
        }
    }

    private String determinePosition(Zone zone, double slideWidth, double slideHeight) {
        long x = zone.getXEmu();
        long y = zone.getYEmu();
        long w = zone.getWidthEmu();
        long h = zone.getHeightEmu();

        // Position verticale (en tenant compte de la hauteur)
        String vertical;
        double yRatio = (double) y / slideHeight;
        double hRatio = (double) h / slideHeight;

        if (yRatio < 0.15) {
            vertical = "top";
        } else if (yRatio + hRatio > 0.85) {
            vertical = "bottom";
        } else {
            vertical = "middle";
        }

        // Position horizontale
        String horizontal;
        double xRatio = (double) x / slideWidth;
        double wRatio = (double) w / slideWidth;

        if (xRatio < 0.15) {
            horizontal = "left";
        } else if (xRatio + wRatio > 0.85) {
            horizontal = "right";
        } else {
            horizontal = "center";
        }

        return vertical + "_" + horizontal;
    }

    private String determineSemanticType(List<Zone> zones) {
        boolean hasCtrTitle = zones.stream().anyMatch(z -> "center_title".equals(z.getZoneType()));
        boolean hasTitle = zones.stream().anyMatch(z -> "title".equals(z.getZoneType()));
        boolean hasSubtitle = zones.stream().anyMatch(z -> "subtitle".equals(z.getZoneType()));
        boolean hasPicture = zones.stream().anyMatch(z -> "picture".equals(z.getZoneType()));
        long bodyCount = zones.stream().filter(z -> "body".equals(z.getZoneType())).count();

        if (hasCtrTitle || (hasTitle && hasSubtitle)) {
            return "TITLE_SLIDE";
        }
        if (hasTitle && bodyCount >= 2 && areTwoColumns(zones)) {
            return "TWO_COLUMN";
        }
        if (hasTitle && hasPicture && bodyCount > 0) {
            return "CONTENT_WITH_MEDIA";
        }
        if (hasTitle && hasPicture && bodyCount == 0) {
            return "SECTION_HEADER";
        }
        if (hasTitle && bodyCount > 0) {
            return "CONTENT";
        }
        if (hasTitle) {
            return "SECTION_HEADER";
        }
        return "CONTENT";
    }

    private boolean areTwoColumns(List<Zone> zones) {
        List<Zone> bodies = zones.stream().filter(z -> "body".equals(z.getZoneType())).toList();
        if (bodies.size() != 2) return false;
        return Math.abs(bodies.get(0).getXEmu() - bodies.get(1).getXEmu()) >
            Math.max(bodies.get(0).getWidthEmu(), bodies.get(1).getWidthEmu()) / 2;
    }

    private StructuralInfo buildStructuralInfo(List<Zone> zones) {
        StructuralInfo info = new StructuralInfo();
        info.setHasFooter(zones.stream().anyMatch(z -> "footer".equals(z.getZoneType())));
        info.setHasSlideNumbers(zones.stream().anyMatch(z ->
            z.getPlaceholder() != null && "sldNum".equals(z.getPlaceholder().getType())));
        info.setHasLogo(false);
        info.setLogoPosition(null);
        info.setHasHeaderBar(false);
        return info;
    }

    private StructuralElements buildStructuralElements(List<SlideLayout> layouts) {
        StructuralElements elements = new StructuralElements();

        boolean hasFooter = layouts.stream()
            .anyMatch(l -> l.getStructuralInfo() != null && l.getStructuralInfo().isHasFooter());
        boolean hasSlideNumbers = layouts.stream()
            .anyMatch(l -> l.getStructuralInfo() != null && l.getStructuralInfo().isHasSlideNumbers());

        elements.setHasFooter(hasFooter);
        elements.setHasSlideNumbers(hasSlideNumbers);
        elements.setHasHeaderBar(false);
        elements.setHasLogo(false);
        elements.setLogoPosition(null);

        return elements;
    }

    private Metadata buildMetadata(String templateName, MainPresentationPart mainPart, int layoutCount) throws Exception {
        Metadata metadata = new Metadata();
        metadata.setAnalysisVersion("1.0");
        metadata.setTemplateOriginalName(templateName);
        metadata.setLayoutCount(layoutCount);
        metadata.setAnalysisDate(Instant.now().toString());

        Presentation pres = mainPart.getContents();
        if (pres.getSldIdLst() != null && pres.getSldIdLst().getSldId() != null) {
            metadata.setSlideCount(pres.getSldIdLst().getSldId().size());
        } else {
            metadata.setSlideCount(0);
        }

        return metadata;
    }

    private String findModelSlide(PresentationMLPackage pptx, SlideLayoutPart layoutPart) {
        for (Part part : pptx.getParts().getParts().values()) {
            if (part instanceof SlidePart) {
                SlidePart slidePart = (SlidePart) part;
                try {
                    if (slidePart.getSlideLayoutPart() != null
                            && slidePart.getSlideLayoutPart().getPartName().equals(layoutPart.getPartName())) {
                        return slidePart.getPartName().toString();
                    }
                } catch (Exception e) {
                    // continue
                }
            }
        }
        return null;
    }


    private void detectBadgeAndSemanticRole(Zone zone) {
        String phType = zone.getPlaceholderType();
        String phName = zone.getPlaceholderName();
        String zoneType = zone.getZoneType();
        double surface = zone.getSurfacePercentage();
        String position = zone.getPosition();

        // 1. Par type de placeholder (le plus fiable)
        if ("num".equals(phType)) {
            zone.setBadge(true);
            zone.setNumber(true);
            zone.setSemanticRole("section_number");
            return;
        }

        if ("sldNum".equals(phType)) {
            zone.setBadge(true);
            zone.setNumber(true);
            zone.setSemanticRole("slide_number");
            return;
        }

        if ("dt".equals(phType)) {
            zone.setDate(true);
            zone.setSemanticRole("date");
            return;
        }

        if ("ftr".equals(phType)) {
            zone.setFooter(true);
            zone.setSemanticRole("footer_text");
            return;
        }

        // 2. Par le nom du placeholder
        if (phName != null) {
            String nameLower = phName.toLowerCase();
            if (nameLower.matches(".*(numéro|number|badge|index).*")) {
                zone.setBadge(true);
                zone.setNumber(true);
                zone.setSemanticRole("section_number");
                return;
            }
            if (nameLower.matches(".*(titre|title|ctrtitle|main title).*") && !zone.isBadge()) {
                zone.setSemanticRole("main_title");
                return;
            }
            if (nameLower.matches(".*(sous-titre|subtitle|sub title).*")) {
                zone.setSemanticRole("subtitle");
                return;
            }
            if (nameLower.matches(".*(corps|body|texte|text|content).*")) {
                zone.setSemanticRole("body_text");
                return;
            }
            if (nameLower.matches(".*(footer|pied|note).*")) {
                zone.setFooter(true);
                zone.setSemanticRole("footer_text");
                return;
            }
            if (nameLower.matches(".*(date).*")) {
                zone.setDate(true);
                zone.setSemanticRole("date");
                return;
            }
            if (nameLower.matches(".*(logo).*")) {
                zone.setImage(true);
                zone.setSemanticRole("image_logo");
                return;
            }
        }

        // 3. Par la position et la taille (fallback)
        if ("center_title".equals(zoneType)) {
            zone.setSemanticRole("main_title");
            return;
        }

        if ("title".equals(zoneType)) {
            zone.setSemanticRole("title");
            return;
        }

        if ("subtitle".equals(zoneType)) {
            zone.setSemanticRole("subtitle");
            return;
        }

        if ("body".equals(zoneType)) {
            if (surface < 15 && position != null && position.contains("center")) {
                zone.setBadge(true);
                zone.setNumber(true);
                zone.setSemanticRole("badge_icon");
            } else {
                zone.setSemanticRole("body_text");
            }
            return;
        }

        if ("picture".equals(zoneType)) {
            zone.setImage(true);
            zone.setSemanticRole(position != null && position.contains("left") ? "image_logo" : "image_illustration");
            return;
        }

        // 4. Détection générique des badges par taille
        if (surface < 8) {
            zone.setBadge(true);
            zone.setNumber(true);
            zone.setSemanticRole("badge_unknown");
            return;
        }

        // 5. Rôle par défaut
        if (zone.getSemanticRole() == null) {
            zone.setSemanticRole("unknown");
        }
    }

    private List<String> detectExpectedContentTypes(Zone zone) {
        List<String> types = new ArrayList<>();
        String semanticRole = zone.getSemanticRole();
        String zoneType = zone.getZoneType();
        boolean isBadge = zone.isBadge();

        // 1. Par rôle sémantique
        if (semanticRole != null) {
            switch (semanticRole) {
                case "section_number":
                case "slide_number":
                    types.add("number");
                    types.add("short_text");
                    return types;

                case "date":
                    types.add("date");
                    return types;

                case "main_title":
                case "title":
                    types.add("title_text");
                    types.add("short_text");
                    return types;

                case "subtitle":
                    types.add("short_text");
                    types.add("title_text");
                    return types;

                case "body_text":
                    types.add("long_text");
                    types.add("bullet_list");
                    return types;

                case "footer_text":
                    types.add("short_text");
                    types.add("caption");
                    return types;

                case "image_logo":
                    types.add("image_url");
                    return types;

                case "image_illustration":
                    types.add("image_url");
                    types.add("image_description");
                    return types;

                case "badge_icon":
                    types.add("short_text");
                    types.add("number");
                    return types;
            }
        }

        // 2. Par type de zone
        if ("center_title".equals(zoneType) || "title".equals(zoneType)) {
            types.add("title_text");
        } else if ("subtitle".equals(zoneType)) {
            types.add("short_text");
        } else if ("body".equals(zoneType)) {
            if (isBadge) {
                types.add("number");
                types.add("short_text");
            } else {
                types.add("long_text");
                types.add("bullet_list");
            }
        } else if ("picture".equals(zoneType)) {
            types.add("image_description");
        } else if ("footer".equals(zoneType)) {
            types.add("short_text");
        }

        // 3. Fallback
        if (types.isEmpty()) {
            types.add("text");
        }

        return types;
    }

    private int calculateMaxChars(Zone zone, Theme theme) {
        double widthInches = zone.getWidthInches();
        double heightInches = zone.getHeightInches();
        ZoneStyle style = zone.getStyle();
        boolean isBadge = zone.isBadge();

        // Pour les badges, on est très restrictif
        if (isBadge) {
            return 5;
        }

        // Estimation de la taille de police
        double fontSize = effectiveFontSize(zone, theme);
        if (style != null && style.getFontSizePt() > 0) {
            fontSize = style.getFontSizePt();
        }

        double marginsInches = (zone.getMargins().getLeftInches() + zone.getMargins().getRightInches());
        double usableWidth = Math.max(0.5, widthInches - marginsInches);
        double usableHeight = Math.max(0.5, heightInches
            - zone.getMargins().getTopInches() - zone.getMargins().getBottomInches());
        double lineHeight = (fontSize / 72.0) * 1.2;
        double averageCharWidth = (fontSize / 72.0) * 0.5;
        int charsPerLine = Math.max(1, (int) (usableWidth / averageCharWidth));
        int lines = Math.max(1, (int) (usableHeight / lineHeight));
        int estimatedChars = (int) (charsPerLine * lines * 0.8);

        // Ajustement par type de zone
        String zoneType = zone.getZoneType();
        if ("title".equals(zoneType) || "center_title".equals(zoneType)) {
            estimatedChars = Math.min(estimatedChars, 50); // Max 50 pour un titre
        } else if ("subtitle".equals(zoneType)) {
            estimatedChars = Math.min(estimatedChars, 30);
        } else if ("body".equals(zoneType)) {
            estimatedChars = Math.min(estimatedChars, 200);
            // Si grande surface, on peut avoir plus de caractères
            if (zone.getSurfacePercentage() > 25) {
                estimatedChars = Math.min(estimatedChars, 400);
            }
        } else if ("footer".equals(zoneType)) {
            estimatedChars = Math.min(estimatedChars, 50);
        }

        return Math.max(estimatedChars, 3);
    }

    private double effectiveFontSize(Zone zone, Theme theme) {
        if (zone.getStyle() != null && zone.getStyle().getFontSizePt() > 0) {
            return zone.getStyle().getFontSizePt();
        }
        if (theme != null && theme.getFonts() != null) {
            FontStyle font = "title".equals(zone.getZoneType()) || "center_title".equals(zone.getZoneType())
                ? theme.getFonts().getTitle()
                : "subtitle".equals(zone.getZoneType()) ? theme.getFonts().getSubtitle()
                : "footer".equals(zone.getZoneType()) ? theme.getFonts().getCaption()
                : theme.getFonts().getBody();
            if (font != null && font.getSizePt() > 0) return font.getSizePt();
        }
        return 18;
    }

    private String buildZoneDescription(Zone zone) {
        String role = zone.getSemanticRole() == null ? zone.getZoneType() : zone.getSemanticRole();
        String expected = zone.getExpectedContentTypes() == null ? "text" : String.join(", ", zone.getExpectedContentTypes());
        return String.format("%s zone %s at %s, %.2f x %.2f inches, capacity about %d characters, expected: %s",
            role, zone.getZoneType(), zone.getPosition(), zone.getWidthInches(), zone.getHeightInches(),
            zone.getMaxChars(), expected);
    }

    private Margins extractMargins(Shape shape) {
        Margins margins = new Margins(0, 0, 0, 0);

        if (shape.getTxBody() == null) return margins;

        CTTextBody txBody = shape.getTxBody();

        // Extraire les marges du body properties
        if (txBody.getBodyPr() != null) {
            CTTextBodyProperties bodyPr = txBody.getBodyPr();
            if (bodyPr.getLIns() != null) {
                margins.setLeft(bodyPr.getLIns());
            }
            if (bodyPr.getRIns() != null) {
                margins.setRight(bodyPr.getRIns());
            }
            if (bodyPr.getTIns() != null) {
                margins.setTop(bodyPr.getTIns());
            }
            if (bodyPr.getBIns() != null) {
                margins.setBottom(bodyPr.getBIns());
            }
        }

        return margins;
    }

}
