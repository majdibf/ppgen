package com.pptxgenerator.pipeline.analyzer;

import com.pptxgenerator.model.StructuralElements;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.PresentationML.MainPresentationPart;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlideMasterPart;
import org.pptx4j.pml.CTPlaceholder;
import org.pptx4j.pml.STPlaceholderType;
import org.pptx4j.pml.Shape;

import java.util.List;

@Slf4j
@ApplicationScoped
public class StructuralElementsDetector {

    // Standard PowerPoint slide dimensions (in EMU)
    private static final long STANDARD_SLIDE_WIDTH = 9144000L;  // 10"
    private static final long STANDARD_SLIDE_HEIGHT = 6858000L; // 7.5"

    /**
     * Detects the recurring structural elements in the template
     */
    public StructuralElements detect(PresentationMLPackage pptx) throws Docx4JException {
        log.info("Détection des éléments structurels...");

        MainPresentationPart mainPart = pptx.getMainPresentationPart();
        long slideWidth = mainPart.getContents().getSldSz().getCx();
        long slideHeight = mainPart.getContents().getSldSz().getCy();

        boolean hasHeaderBar = false;
        boolean hasFooter = false;
        boolean hasSlideNumbers = false;

        // 1. Analyze the Slide Master
        for (SlideMasterPart master : getSlideMasterParts(pptx)) {
            if (master.getContents().getCSld() == null) continue;
            if (master.getContents().getCSld().getSpTree() == null) continue;

            for (Object shapeObj : master.getContents().getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame()) {
                if (!(shapeObj instanceof Shape shape)) continue;

                try {
                    // Footer: text at the bottom of the slide
                    if (hasTextFrame(shape) && shape.getTxBody() != null) {
                        if (shape.getSpPr().getXfrm().getOff().getY() > slideHeight * 0.9) {
                            hasFooter = true;
                        }
                    }

                    // Header bar: colored shape at the top
                    if (isAutoShape(shape) && shape.getSpPr().getXfrm().getOff().getY() < slideHeight * 0.1) {
                        hasHeaderBar = true;
                    }
                } catch (Exception e) {
                    log.debug("Erreur analyse shape du master: {}", e.getMessage());
                }
            }
        }

        // 2. Detect slide numbering in the layouts
        for (SlideLayoutPart layout : getSlideLayoutParts(pptx)) {
            if (layout.getContents().getCSld() == null) continue;
            if (layout.getContents().getCSld().getSpTree() == null) continue;

            for (Object shapeObj : layout.getContents().getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame()) {
                if (!(shapeObj instanceof Shape shape)) continue;
                if (shape.getNvSpPr() == null || shape.getNvSpPr().getNvPr() == null) continue;

                CTPlaceholder ph = shape.getNvSpPr().getNvPr().getPh();
                if (ph != null && ph.getType() == STPlaceholderType.SLD_NUM) {
                    hasSlideNumbers = true;
                    break;
                }
            }
            if (hasSlideNumbers) break;
        }

        StructuralElements elements = StructuralElements.builder()
            .hasHeaderBar(hasHeaderBar)
            .hasFooter(hasFooter)
            .hasSlideNumbers(hasSlideNumbers)
            .build();

        log.info("Éléments structurels détectés: header={}, footer={}, numbers={}",
            hasHeaderBar, hasFooter, hasSlideNumbers);

        return elements;
    }

    private List<SlideMasterPart> getSlideMasterParts(PresentationMLPackage pptx) {
        return pptx.getParts().getParts().values().stream()
            .filter(SlideMasterPart.class::isInstance)
            .map(SlideMasterPart.class::cast)
            .toList();
    }

    private List<SlideLayoutPart> getSlideLayoutParts(PresentationMLPackage pptx) {
        return pptx.getParts().getParts().values().stream()
            .filter(SlideLayoutPart.class::isInstance)
            .map(SlideLayoutPart.class::cast)
            .toList();
    }

    private boolean hasTextFrame(Shape shape) {
        return shape.getSpPr() != null && shape.getSpPr().getXfrm() != null;
    }

    private boolean isAutoShape(Shape shape) {
        return shape.getSpPr() != null && shape.getSpPr().getPrstGeom() != null;
    }
}
