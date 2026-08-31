package com.pptxgenerator.pipeline.renderer;

import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.ZoneKeys;
import com.pptxgenerator.model.enums.ZoneType;
import com.pptxgenerator.pipeline.renderer.model.RenderWarning;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.XmlUtils;
import org.docx4j.dml.*;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.pptx4j.jaxb.Context;
import org.pptx4j.pml.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Injects planned content into a slide.
 *
 * <p>Three responsibilities, kept as separate steps for clarity:
 * <ol>
 *   <li>clone every placeholder defined by the layout into the slide (valid XML);</li>
 *   <li>resolve each planned {@link Zone} to its cloned shape via {@link PlaceholderMapper};</li>
 *   <li>fill the matched shapes with the zone's text (provided as a flat map), honouring the
 *       zone type.</li>
 * </ol>
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PlaceholderInjector {

    /** Starting id for cloned shapes, avoiding collisions with template-reserved ids. */
    private static final long SHAPE_ID_START = 1000L;

    private final PlaceholderMapper mapper;

    /**
     * Injects the resolved {@code zoneText} into {@code slidePart}, guided by the layout's zones.
     *
     * @param slidePart   the slide to fill
     * @param zoneText    flat map of {@code zoneKey -> text} (see {@link SlideContentZoneResolver})
     * @param layoutPart  the slide's layout (cloned placeholders source)
     * @param layoutZones zones declared for the slide's layout
     * @return warnings collected while injecting (empty on full success)
     */
    public List<RenderWarning> inject(SlidePart slidePart,
                                      Map<String, String> zoneText,
                                      SlideLayoutPart layoutPart,
                                      List<Zone> layoutZones) {
        List<RenderWarning> warnings = new ArrayList<>();
        if (layoutZones == null || layoutZones.isEmpty() || zoneText == null || zoneText.isEmpty()) {
            return warnings;
        }

        try {
            SldLayout layout = layoutPart.getContents();
            if (layout.getCSld() == null || layout.getCSld().getSpTree() == null) {
                log.warn("Layout '{}' has no shape tree; nothing to clone into slide '{}'.",
                        layoutPart.getPartName(), slidePart.getPartName());
                return warnings;
            }

            Sld slide = slidePart.getContents();
            if (slide.getCSld() == null) {
                slide.setCSld(new CommonSlideData());
            }
            if (slide.getCSld().getSpTree() == null) {
                slide.getCSld().setSpTree(new GroupShape());
            }

            int clonedCount = cloneLayoutPlaceholders(slide.getCSld().getSpTree(), layout.getCSld().getSpTree());
            log.debug("Cloned {} placeholder(s) from layout into slide.", clonedCount);

            Map<String, Shape> zoneKeyToShape = mapper.mapPlaceholders(slidePart, layoutZones);

            int injectedCount = 0;
            for (Zone zone : layoutZones) {
                String zoneKey = ZoneKeys.key(zone);
                Shape shape = zoneKeyToShape.get(zoneKey);
                if (shape == null || shape.getTxBody() == null) {
                    continue;
                }
                String text = zoneText.get(zoneKey);
                if (text == null || text.isEmpty()) {
                    continue;
                }
                shape.getTxBody().getP().clear();
                injectZoneText(shape, text, zone.getZoneType());
                injectedCount++;
            }
            log.debug("Injected content into {} zone(s).", injectedCount);

        } catch (Exception e) {
            log.error("Placeholder injection failed for slide '{}'.", slidePart.getPartName(), e);
            warnings.add(RenderWarning.builder()
                    .code("INJECTION_FAILED")
                    .message(e.getMessage())
                    .build());
        }
        return warnings;
    }

    /** Deep-copies every layout placeholder into the slide's shape tree, with fresh ids. */
    private int cloneLayoutPlaceholders(GroupShape slideSpTree, GroupShape layoutSpTree) {
        long shapeIdCounter = SHAPE_ID_START;
        int clonedCount = 0;
        for (Shape layoutShape : PlaceholderMapper.extractPlaceholders(layoutSpTree)) {
            Shape cloned = (Shape) XmlUtils.deepCopy(layoutShape, Context.jcPML);
            cloned.getNvSpPr().getCNvPr().setId(shapeIdCounter);
            cloned.getNvSpPr().getCNvPr().setName("Placeholder_" + shapeIdCounter);
            shapeIdCounter++;
            slideSpTree.getSpOrGrpSpOrGraphicFrame().add(cloned);
            clonedCount++;
        }
        return clonedCount;
    }

    /** Dispatches text injection according to the zone type. */
    private void injectZoneText(Shape shape, String text, ZoneType zoneType) {
        switch (zoneType) {
            case TITLE, SUBTITLE, LINE, WORD -> addParagraph(shape, text, false, 0, false);
            case CENTER_TITLE -> addParagraph(shape, text, false, 0, true);
            case BODY -> injectBodyText(shape, text);
            case PICTURE, CHART, TABLE, BACKGROUND -> log.debug("Zone type {} is not text-filled by this renderer.",
                    zoneType);
            default -> addParagraph(shape, text, false, 0, false);
        }
    }

    private void injectBodyText(Shape shape, String text) {
        if (text.contains("\n\n")) {
            for (String paragraph : text.split("\n\n")) {
                addBullets(shape, paragraph.split("\n"));
            }
        } else if (text.contains("\n")) {
            addBullets(shape, text.split("\n"));
        } else {
            addParagraph(shape, text, false, 0, false);
        }
    }

    private void addBullets(Shape shape, String[] lines) {
        for (String line : lines) {
            line = line.trim();
            if (line.isBlank()) {
                continue;
            }
            boolean isBullet = false;
            int level = 0;
            if (line.startsWith("- ") || line.startsWith("• ")) {
                line = line.substring(2);
                isBullet = true;
            } else if (line.startsWith("  - ") || line.startsWith("  • ")) {
                line = line.substring(4);
                isBullet = true;
                level = 1;
            }
            addParagraph(shape, line, isBullet, level, false);
        }
    }

    private void addParagraph(Shape shape, String text, boolean isBullet, int level, boolean centered) {
        if (text == null || text.isBlank() || shape.getTxBody() == null) {
            return;
        }
        CTTextParagraph p = new CTTextParagraph();
        if (isBullet || centered) {
            CTTextParagraphProperties pPr = new CTTextParagraphProperties();
            if (isBullet) {
                pPr.setLvl(level);
            }
            if (centered) {
                pPr.setAlgn(STTextAlignType.CTR);
            }
            p.setPPr(pPr);
        }
        CTRegularTextRun run = new CTRegularTextRun();
        run.setT(text);
        p.getEGTextRun().add(run);
        shape.getTxBody().getP().add(p);
    }
}
