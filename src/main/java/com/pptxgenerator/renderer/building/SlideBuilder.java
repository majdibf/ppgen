package com.pptxgenerator.renderer.building;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.PresentationML.MainPresentationPart;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;

/**
 * Crée une nouvelle slide basée sur un layout.
 */
@Slf4j
@ApplicationScoped
public class SlideBuilder {

    /**
     * Crée une slide à partir d'un layout.
     */
    public SlidePart createSlide(PresentationMLPackage pptx, SlideLayoutPart layoutPart, int slideIndex) throws Exception {
        MainPresentationPart mainPart = pptx.getMainPresentationPart();

        PartName slidePartName = new PartName("/ppt/slides/slide" + (slideIndex + 1) + ".xml");

        // createSlidePart enregistre automatiquement la slide dans sldIdLst et les relations
        SlidePart slidePart = PresentationMLPackage.createSlidePart(mainPart, layoutPart, slidePartName);

        log.debug("Slide {} créée avec layout {}", slideIndex + 1, layoutPart.getPartName());
        return slidePart;
    }
}
