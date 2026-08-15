package com.pptxgenerator.analyzer;

import com.pptxgenerator.model.*;
import org.docx4j.dml.BaseStyles;
import org.docx4j.dml.CTColor;
import org.docx4j.dml.CTColorScheme;
import org.docx4j.dml.CTRegularTextRun;
import org.docx4j.dml.CTTextBody;
import org.docx4j.dml.CTTextCharacterProperties;
import org.docx4j.dml.CTTextParagraph;
import org.docx4j.dml.CTTextParagraphProperties;
import org.docx4j.dml.CTTransform2D;
import org.docx4j.dml.FontCollection;
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
        structure.setLayouts(extractLayouts(pptx, mainPart));
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

    private List<SlideLayout> extractLayouts(PresentationMLPackage pptx, MainPresentationPart mainPart) throws Exception {
        List<SlideLayout> layouts = new ArrayList<>();
        int layoutIndex = 0;

        for (Part part : pptx.getParts().getParts().values()) {
            if (part instanceof SlideLayoutPart) {
                SlideLayoutPart layoutPart = (SlideLayoutPart) part;
                SlideLayout layout = analyzeLayout(layoutPart, layoutIndex, pptx);
                layouts.add(layout);
                layoutIndex++;
            }
        }

        return layouts;
    }

    private SlideLayout analyzeLayout(SlideLayoutPart layoutPart, int index, PresentationMLPackage pptx) throws Exception {
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

                        Zone zone = analyzeZone(shape, zoneId);
                        if (zone != null) {
                            zones.add(zone);
                            zoneId++;
                        }
                    }
                }
            }
        }

        assignReadingOrderAndPosition(zones);
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

    private Zone analyzeZone(Shape shape, int zoneId) {
        Zone zone = new Zone();
        zone.setZoneId(zoneId);

        CTPlaceholder ph = shape.getNvSpPr().getNvPr().getPh();
        String phType = (ph.getType() != null) ? ph.getType().value() : "body";
        int phIdx = (int) ph.getIdx();

        PlaceholderInfo placeholder = new PlaceholderInfo();
        placeholder.setType(phType);
        placeholder.setIdx(phIdx);
        placeholder.setName(shape.getNvSpPr().getCNvPr() != null ? shape.getNvSpPr().getCNvPr().getName() : "");
        placeholder.setHasText(hasText(shape));
        zone.setPlaceholder(placeholder);

        zone.setZoneType(mapPlaceholderType(phType));

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

        zone.setStyle(extractZoneStyle(shape));

        if ("picture".equals(phType) || "pic".equals(phType)) {
            ImageInfo imgInfo = new ImageInfo();
            imgInfo.setName(placeholder.getName());
            imgInfo.setPlaceholder(true);
            zone.setImageInfo(imgInfo);
        }

        return zone;
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

    private void assignReadingOrderAndPosition(List<Zone> zones) {
        zones.sort((a, b) -> {
            int yDiff = Long.compare(a.getYEmu(), b.getYEmu());
            if (yDiff != 0) return yDiff;
            return Long.compare(a.getXEmu(), b.getXEmu());
        });

        for (int i = 0; i < zones.size(); i++) {
            zones.get(i).setReadingOrder(i + 1);
            zones.get(i).setPosition(determinePosition(zones.get(i)));
        }
    }

    private void calculateSurfacePercentages(List<Zone> zones, PresentationMLPackage pptx) throws Exception {
        MainPresentationPart mainPart = pptx.getMainPresentationPart();
        Presentation.SldSz sldSz = mainPart.getContents().getSldSz();
        double slideArea = (double) sldSz.getCx() * sldSz.getCy();

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

    private String determinePosition(Zone zone) {
        long x = zone.getXEmu();
        long y = zone.getYEmu();
        long w = zone.getWidthEmu();
        long h = zone.getHeightEmu();

        String vertPos;
        if (y < 2000000) vertPos = "top";
        else if (y + h / 2 < 4000000) vertPos = "middle";
        else vertPos = "bottom";

        String horizPos;
        long slideWidth = 12192000L;
        long centerX = x + w / 2;
        if (centerX < slideWidth / 3) horizPos = "left";
        else if (centerX < 2 * slideWidth / 3) horizPos = "center";
        else horizPos = "right";

        return vertPos + "_" + horizPos;
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
}
