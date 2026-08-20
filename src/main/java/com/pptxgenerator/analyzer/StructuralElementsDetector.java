package com.pptxgenerator.analyzer;

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

    // Dimensions standard d'une slide PowerPoint (en EMU)
    private static final long STANDARD_SLIDE_WIDTH = 9144000L;  // 10"
    private static final long STANDARD_SLIDE_HEIGHT = 6858000L; // 7.5"

    /**
     * Détecte les éléments structurels récurrents dans le template
     */
    public StructuralElements detect(PresentationMLPackage pptx) throws Docx4JException {
        log.info("Détection des éléments structurels...");

        MainPresentationPart mainPart = pptx.getMainPresentationPart();
        long slideWidth = mainPart.getContents().getSldSz().getCx();
        long slideHeight = mainPart.getContents().getSldSz().getCy();

        boolean hasHeaderBar = false;
        boolean hasFooter = false;
        boolean hasSlideNumbers = false;
        boolean hasLogo = false;
        String logoPosition = null;

        // 1. Analyse du Slide Master
        for (SlideMasterPart master : getSlideMasterParts(pptx)) {
            if (master.getContents().getCSld() == null) continue;
            if (master.getContents().getCSld().getSpTree() == null) continue;

            for (Object shapeObj : master.getContents().getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame()) {
                if (!(shapeObj instanceof Shape shape)) continue;

                try {
                    // Footer : texte en bas de la slide
                    if (hasTextFrame(shape) && shape.getTxBody() != null) {
                        if (shape.getSpPr().getXfrm().getOff().getY() > slideHeight * 0.9) {
                            hasFooter = true;
                        }
                    }

                    // Header bar : forme colorée en haut
                    if (isAutoShape(shape) && shape.getSpPr().getXfrm().getOff().getY() < slideHeight * 0.1) {
                        hasHeaderBar = true;
                    }

                    // Logo : image
                    if (isPicture(shape)) {
                        hasLogo = true;
                        logoPosition = detectLogoPosition(shape, slideWidth, slideHeight);
                    }
                } catch (Exception e) {
                    log.debug("Erreur analyse shape du master: {}", e.getMessage());
                }
            }
        }

        // 2. Détection de la numérotation dans les layouts
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
            .hasLogo(hasLogo)
            .logoPosition(logoPosition)
            .build();

        log.info("Éléments structurels détectés: header={}, footer={}, numbers={}, logo={} ({})",
            hasHeaderBar, hasFooter, hasSlideNumbers, hasLogo, logoPosition);

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

    private boolean isPicture(Shape shape) {
        // Dans OOXML, les images sont des Pic, pas des Shape
        // On vérifie via le type de l'objet
        return false; // Simplifié - à adapter selon la structure JAXB réelle
    }

    private String detectLogoPosition(Shape shape, long slideWidth, long slideHeight) {
        long x = shape.getSpPr().getXfrm().getOff().getX();
        long y = shape.getSpPr().getXfrm().getOff().getY();

        boolean isLeft = x < slideWidth * 0.3;
        boolean isRight = x > slideWidth * 0.7;
        boolean isTop = y < slideHeight * 0.3;
        boolean isBottom = y > slideHeight * 0.7;

        if (isLeft && isTop) return "top_left";
        if (isRight && isTop) return "top_right";
        if (isLeft && isBottom) return "bottom_left";
        if (isRight && isBottom) return "bottom_right";
        return "unknown";
    }
}
