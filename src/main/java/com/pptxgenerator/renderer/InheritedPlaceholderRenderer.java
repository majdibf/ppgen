package com.pptxgenerator.renderer;

import com.pptxgenerator.model.SlideLayout;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.ZoneContent;
import org.docx4j.dml.CTNonVisualDrawingProps;
import org.docx4j.dml.CTNonVisualDrawingShapeProps;
import org.docx4j.dml.CTTextBody;
import org.docx4j.dml.CTTextBodyProperties;
import org.docx4j.dml.CTTextListStyle;
import org.docx4j.dml.CTTextParagraph;
import org.docx4j.dml.CTRegularTextRun;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.pptx4j.pml.CommonSlideData;
import org.pptx4j.pml.CTPlaceholder;
import org.pptx4j.pml.GroupShape;
import org.pptx4j.pml.NvPr;
import org.pptx4j.pml.Sld;
import org.pptx4j.pml.SldLayout;
import org.pptx4j.pml.STPlaceholderType;
import org.pptx4j.pml.Shape;

import java.util.List;

/**
 * Prototype for placeholder-only rendering.
 *
 * <p>This class deliberately does not copy layout shapes and is not wired into
 * {@link PresentationRenderer} yet. The layout/master remains the source of
 * geometry and styling; the slide receives only a minimal placeholder binding
 * and its text.</p>
 */
public class InheritedPlaceholderRenderer {

    public void addTextPlaceholders(SlidePart slidePart, SlideLayoutPart layoutPart,
                                    SlideLayout layout, List<ZoneContent> contents) throws Exception {
        Sld slide = slidePart.getContents();
        if (slide.getCSld() == null) slide.setCSld(new CommonSlideData());
        if (slide.getCSld().getSpTree() == null) slide.getCSld().setSpTree(new GroupShape());

        List<Object> shapes = slide.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame();
        int nextShapeId = shapes.size() + 2;
        for (ZoneContent content : contents) {
            if (content.getContent() == null || content.getContent().isBlank()) continue;
            Zone zone = layout.getZones().stream()
                .filter(candidate -> candidate.getZoneId() == content.getZoneId())
                .findFirst().orElse(null);
            if (zone == null || "footer".equals(zone.getZoneType())) continue;
            shapes.add(createPlaceholderBinding(zone, content.getContent(), nextShapeId++));
        }
    }

    private Shape createPlaceholderBinding(Zone zone, String text, int shapeId) {
        Shape shape = new Shape();
        Shape.NvSpPr nvSpPr = new Shape.NvSpPr();

        CTNonVisualDrawingProps drawingProps = new CTNonVisualDrawingProps();
        drawingProps.setId(shapeId);
        drawingProps.setName("Content placeholder " + shapeId);
        nvSpPr.setCNvPr(drawingProps);
        nvSpPr.setCNvSpPr(new CTNonVisualDrawingShapeProps());

        NvPr nvPr = new NvPr();
        CTPlaceholder placeholder = new CTPlaceholder();
        placeholder.setIdx(zone.getPlaceholder() == null
            ? (long) zone.getZoneId() : (long) zone.getPlaceholder().getIdx());
        placeholder.setType(resolvePlaceholderType(zone));
        nvPr.setPh(placeholder);
        nvSpPr.setNvPr(nvPr);
        shape.setNvSpPr(nvSpPr);
        shape.setTxBody(createTextBody(text));
        return shape;
    }

    private STPlaceholderType resolvePlaceholderType(Zone zone) {
        return switch (zone.getPlaceholderType() == null ? zone.getZoneType() : zone.getPlaceholderType()) {
            case "title" -> STPlaceholderType.TITLE;
            case "ctrTitle", "center_title" -> STPlaceholderType.CTR_TITLE;
            case "subTitle", "subtitle" -> STPlaceholderType.SUB_TITLE;
            case "pic", "picture" -> STPlaceholderType.OBJ;
            default -> STPlaceholderType.BODY;
        };
    }

    private CTTextBody createTextBody(String text) {
        CTTextBody body = new CTTextBody();
        body.setBodyPr(new CTTextBodyProperties());
        body.setLstStyle(new CTTextListStyle());
        CTTextParagraph paragraph = new CTTextParagraph();
        CTRegularTextRun run = new CTRegularTextRun();
        run.setT(text);
        paragraph.getEGTextRun().add(run);
        body.getP().add(paragraph);
        return body;
    }
}
