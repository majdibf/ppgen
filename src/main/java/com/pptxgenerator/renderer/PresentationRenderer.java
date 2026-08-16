package com.pptxgenerator.renderer;

import com.pptxgenerator.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.dml.*;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.PresentationML.MainPresentationPart;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart;
import org.pptx4j.pml.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@ApplicationScoped
public class PresentationRenderer {

    public File render(String templateFilePath, TemplateStructure template, EnrichedPlan enrichedPlan, 
                      ContentMap contentMap, String outputPath) throws Exception {
        log.info("Rendering presentation: {}", enrichedPlan.getTitle());
        
        PresentationMLPackage pptx = PresentationMLPackage.load(new File(templateFilePath));
        
        MainPresentationPart mainPart = pptx.getMainPresentationPart();
        
        // Remove all existing slides from template
        removeExistingSlides(pptx, mainPart);
        
        // Create new slides with content
        for (int i = 0; i < enrichedPlan.getSlides().size(); i++) {
            EnrichedSlide slide = enrichedPlan.getSlides().get(i);
            String slideId = "slide_" + i;
            SlideContent content = contentMap.getSlideContent(slideId);
            
            log.debug("Rendering slide {}: {}", i, slide.getTitle());
            
            if (slide.getAssignedLayout() != null) {
                renderSlideWithLayout(pptx, mainPart, slide, content, i);
            } else {
                log.warn("Slide {} has no layout, skipping", slideId);
            }
        }
        
        File outputFile = new File(outputPath);
        pptx.save(outputFile);
        
        log.info("Presentation rendered successfully: {}", outputPath);
        return outputFile;
    }
    
    private void removeExistingSlides(PresentationMLPackage pptx, MainPresentationPart mainPart) throws Exception {
        Presentation presentation = mainPart.getContents();
        
        if (presentation.getSldIdLst() == null || presentation.getSldIdLst().getSldId() == null) {
            return;
        }
        
        List<Presentation.SldIdLst.SldId> slidesToRemove = new ArrayList<>(presentation.getSldIdLst().getSldId());
        
        for (Presentation.SldIdLst.SldId sldId : slidesToRemove) {
            String rId = sldId.getRid();
            if (rId != null) {
                try {
                    RelationshipsPart rp = mainPart.getRelationshipsPart();
                    org.docx4j.relationships.Relationship rel = rp.getRelationshipByID(rId);
                    if (rel != null) {
                        Part part = rp.getPart(rel);
                        if (part instanceof SlidePart) {
                            pptx.getParts().remove(part.getPartName());
                            rp.removeRelationship(rel);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not remove slide with rId {}: {}", rId, e.getMessage());
                }
            }
        }
        
        presentation.getSldIdLst().getSldId().clear();
        log.info("Removed {} existing slides from template", slidesToRemove.size());
    }
    
    private void renderSlideWithLayout(PresentationMLPackage pptx, MainPresentationPart mainPart,
                                       EnrichedSlide slide, SlideContent content, int slideIndex) throws Exception {
        SlideLayout layout = slide.getAssignedLayout();
        String layoutPath = layout.getOriginalName();
        
        SlideLayoutPart layoutPart = (SlideLayoutPart) pptx.getParts().getParts().values().stream()
            .filter(p -> p instanceof SlideLayoutPart)
            .filter(p -> p.getPartName().toString().equals(layoutPath))
            .findFirst()
            .orElse(null);
        
        if (layoutPart == null) {
            log.warn("Layout not found: {}, using default", layoutPath);
            return;
        }
        
        PartName slidePartName = new PartName("/ppt/slides/slide" + (slideIndex + 1) + ".xml");
        SlidePart slidePart = PresentationMLPackage.createSlidePart(mainPart, layoutPart, slidePartName);
        
        // Create shapes in slide based on layout zones and content
        if (content != null && content.getZoneContents() != null && layout.getZones() != null) {
            createShapesInSlide(slidePart, layout, content);
        }
        
        addSlideToPresentation(mainPart, slidePart, slideIndex);
    }
    
    private void createShapesInSlide(SlidePart slidePart, SlideLayout layout, SlideContent content) throws Exception {
        Sld sld = slidePart.getContents();
        
        if (sld.getCSld() == null) {
            sld.setCSld(new CommonSlideData());
        }
        if (sld.getCSld().getSpTree() == null) {
            sld.getCSld().setSpTree(new GroupShape());
        }
        
        List<Object> slideShapes = sld.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame();
        
        for (ZoneContent zoneContent : content.getZoneContents()) {
            // Find matching zone in layout
            Zone layoutZone = layout.getZones().stream()
                .filter(z -> z.getZoneId() == zoneContent.getZoneId())
                .findFirst()
                .orElse(null);
            
            if (layoutZone == null) {
                continue;
            }
            
            String zoneType = zoneContent.getZoneType();
            
            // Skip zones with no content at all (except picture which gets a placeholder)
            boolean hasTextContent = zoneContent.getContent() != null && !zoneContent.getContent().isEmpty();
            boolean hasImageContent = "picture".equals(zoneType) && zoneContent.getImageDescription() != null;
            
            if (!hasTextContent && !hasImageContent) {
                continue;
            }
            
            // Create shape for this zone
            Shape shape;
            if ("picture".equals(zoneType)) {
                shape = createPicturePlaceholderShape(zoneContent, layoutZone, slideShapes.size() + 2);
            } else {
                shape = createShapeForZone(zoneContent, layoutZone, slideShapes.size() + 2);
            }
            slideShapes.add(shape);
            log.debug("Created shape for zone {} type={}", zoneContent.getZoneId(), zoneType);
        }
    }
    
    private Shape createPicturePlaceholderShape(ZoneContent zoneContent, Zone layoutZone, int shapeId) {
        Shape shape = new Shape();
        
        Shape.NvSpPr nvSpPr = new Shape.NvSpPr();
        
        CTNonVisualDrawingProps cNvPr = new CTNonVisualDrawingProps();
        cNvPr.setId(shapeId);
        cNvPr.setName("Image Placeholder " + shapeId);
        nvSpPr.setCNvPr(cNvPr);
        
        CTNonVisualDrawingShapeProps cNvSpPr = new CTNonVisualDrawingShapeProps();
        nvSpPr.setCNvSpPr(cNvSpPr);
        
        NvPr nvPr = new NvPr();
        CTPlaceholder ph = new CTPlaceholder();
        ph.setType(STPlaceholderType.OBJ);
        ph.setIdx((long) zoneContent.getZoneId());
        nvPr.setPh(ph);
        nvSpPr.setNvPr(nvPr);
        
        shape.setNvSpPr(nvSpPr);
        
        CTShapeProperties spPr = new CTShapeProperties();
        CTTransform2D xfrm = new CTTransform2D();
        
        CTPoint2D off = new CTPoint2D();
        off.setX(layoutZone.getXEmu());
        off.setY(layoutZone.getYEmu());
        xfrm.setOff(off);
        
        CTPositiveSize2D ext = new CTPositiveSize2D();
        ext.setCx(layoutZone.getWidthEmu());
        ext.setCy(layoutZone.getHeightEmu());
        xfrm.setExt(ext);
        
        spPr.setXfrm(xfrm);
        
        CTPresetGeometry2D prstGeom = new CTPresetGeometry2D();
        prstGeom.setPrst(STShapeType.RECT);
        spPr.setPrstGeom(prstGeom);
        
        // Light gray fill to indicate image placeholder
        CTSolidColorFillProperties fill = new CTSolidColorFillProperties();
        CTSRgbColor color = new CTSRgbColor();
        color.setVal("E0E0E0");
        fill.setSrgbClr(color);
        spPr.setSolidFill(fill);
        
        // Border
        CTLineProperties ln = new CTLineProperties();
        CTPresetLineDashProperties prstDash = new CTPresetLineDashProperties();
        prstDash.setVal(STPresetLineDashVal.DASH);
        ln.setPrstDash(prstDash);
        spPr.setLn(ln);
        
        shape.setSpPr(spPr);
        
        // Text body with image description
        CTTextBody txBody = new CTTextBody();
        
        CTTextBodyProperties bodyPr = new CTTextBodyProperties();
        bodyPr.setAnchor(STTextAnchoringType.CTR);
        txBody.setBodyPr(bodyPr);
        
        CTTextListStyle lstStyle = new CTTextListStyle();
        txBody.setLstStyle(lstStyle);
        
        CTTextParagraph paragraph = new CTTextParagraph();
        CTTextParagraphProperties pPr = new CTTextParagraphProperties();
        pPr.setAlgn(STTextAlignType.CTR);
        paragraph.setPPr(pPr);
        
        String description = zoneContent.getImageDescription() != null 
            ? zoneContent.getImageDescription() 
            : "[Image]";
        
        CTRegularTextRun run = new CTRegularTextRun();
        run.setT(description);
        
        CTTextCharacterProperties rPr = new CTTextCharacterProperties();
        rPr.setSz(1200);
        TextFont latin = new TextFont();
        latin.setTypeface("Arial");
        rPr.setLatin(latin);
        
        // Gray color for placeholder text
        CTSolidColorFillProperties textFill = new CTSolidColorFillProperties();
        CTSRgbColor textColor = new CTSRgbColor();
        textColor.setVal("808080");
        textFill.setSrgbClr(textColor);
        rPr.setSolidFill(textFill);
        
        run.setRPr(rPr);
        paragraph.getEGTextRun().add(run);
        txBody.getP().add(paragraph);
        
        shape.setTxBody(txBody);
        
        return shape;
    }
    
    private Shape createShapeForZone(ZoneContent zoneContent, Zone layoutZone, int shapeId) {
        Shape shape = new Shape();
        
        // Non-visual properties
        Shape.NvSpPr nvSpPr = new Shape.NvSpPr();
        
        CTNonVisualDrawingProps cNvPr = new CTNonVisualDrawingProps();
        cNvPr.setId(shapeId);
        cNvPr.setName("Placeholder " + shapeId);
        nvSpPr.setCNvPr(cNvPr);
        
        CTNonVisualDrawingShapeProps cNvSpPr = new CTNonVisualDrawingShapeProps();
        nvSpPr.setCNvSpPr(cNvSpPr);
        
        NvPr nvPr = new NvPr();
        // Add placeholder info
        CTPlaceholder ph = new CTPlaceholder();
        String zoneType = zoneContent.getZoneType();
        if ("title".equals(zoneType)) {
            ph.setType(STPlaceholderType.TITLE);
            ph.setIdx(0L);
        } else if ("center_title".equals(zoneType)) {
            ph.setType(STPlaceholderType.CTR_TITLE);
            ph.setIdx(0L);
        } else if ("subtitle".equals(zoneType)) {
            ph.setType(STPlaceholderType.SUB_TITLE);
            ph.setIdx(1L);
        } else {
            ph.setType(STPlaceholderType.BODY);
            ph.setIdx((long) zoneContent.getZoneId());
        }
        nvPr.setPh(ph);
        nvSpPr.setNvPr(nvPr);
        
        shape.setNvSpPr(nvSpPr);
        
        // Shape properties with position and size from layout
        CTShapeProperties spPr = new CTShapeProperties();
        CTTransform2D xfrm = new CTTransform2D();
        
        CTPoint2D off = new CTPoint2D();
        off.setX(layoutZone.getXEmu());
        off.setY(layoutZone.getYEmu());
        xfrm.setOff(off);
        
        CTPositiveSize2D ext = new CTPositiveSize2D();
        ext.setCx(layoutZone.getWidthEmu());
        ext.setCy(layoutZone.getHeightEmu());
        xfrm.setExt(ext);
        
        spPr.setXfrm(xfrm);
        
        // Preset geometry
        CTPresetGeometry2D prstGeom = new CTPresetGeometry2D();
        prstGeom.setPrst(STShapeType.RECT);
        spPr.setPrstGeom(prstGeom);
        
        shape.setSpPr(spPr);
        
        // Text body with content
        CTTextBody txBody = new CTTextBody();
        
        CTTextBodyProperties bodyPr = new CTTextBodyProperties();
        bodyPr.setWrap(STTextWrappingType.SQUARE);
        txBody.setBodyPr(bodyPr);
        
        CTTextListStyle lstStyle = new CTTextListStyle();
        txBody.setLstStyle(lstStyle);
        
        CTTextParagraph paragraph = new CTTextParagraph();
        
        CTTextParagraphProperties pPr = new CTTextParagraphProperties();
        paragraph.setPPr(pPr);
        
        CTRegularTextRun run = new CTRegularTextRun();
        run.setT(zoneContent.getContent());
        
        CTTextCharacterProperties rPr = new CTTextCharacterProperties();
        
        // Apply style from layout zone
        if (layoutZone.getStyle() != null) {
            if (layoutZone.getStyle().getFontSizePt() > 0) {
                rPr.setSz(layoutZone.getStyle().getFontSizePt() * 100);
            }
            if ("Bold".equals(layoutZone.getStyle().getFontWeight())) {
                rPr.setB(true);
            }
            if (layoutZone.getStyle().getFontFamily() != null) {
                TextFont latin = new TextFont();
                latin.setTypeface(layoutZone.getStyle().getFontFamily());
                rPr.setLatin(latin);
            }
        }
        
        run.setRPr(rPr);
        paragraph.getEGTextRun().add(run);
        txBody.getP().add(paragraph);
        
        shape.setTxBody(txBody);
        
        return shape;
    }
    
    private void addSlideToPresentation(MainPresentationPart mainPart, SlidePart slidePart, int slideIndex) throws Exception {
        Presentation presentation = mainPart.getContents();
        
        if (presentation.getSldIdLst() == null) {
            presentation.setSldIdLst(new Presentation.SldIdLst());
        }
        
        // Find the relationship ID for this slide
        RelationshipsPart rp = mainPart.getRelationshipsPart();
        String targetRId = null;
        for (org.docx4j.relationships.Relationship rel : rp.getRelationships().getRelationship()) {
            if (rel.getTarget().equals(slidePart.getPartName().toString())) {
                targetRId = rel.getId();
                break;
            }
        }
        
        if (targetRId == null) {
            log.warn("Could not find relationship for slide {}", slidePart.getPartName());
            return;
        }
        
        // Create final variable for lambda
        final String finalTargetRId = targetRId;
        
        // Check if this relationship ID is already in the slide list
        boolean alreadyExists = presentation.getSldIdLst().getSldId().stream()
            .anyMatch(sldId -> finalTargetRId.equals(sldId.getRid()));
        
        if (alreadyExists) {
            log.debug("Slide {} already in presentation, skipping duplicate", slidePart.getPartName());
            return;
        }
        
        Presentation.SldIdLst.SldId sldId = new Presentation.SldIdLst.SldId();
        sldId.setId((long) (256 + slideIndex));
        sldId.setRid(targetRId);
        
        presentation.getSldIdLst().getSldId().add(sldId);
    }
}
