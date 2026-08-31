package com.pptxgenerator.pipeline.renderer;

import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.TemplateAnalysis;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.PresentationML.MainPresentationPart;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart;
import org.docx4j.relationships.Relationship;
import org.pptx4j.pml.Presentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds slides for the renderer: resolves a logical layout id to its concrete
 * {@link SlideLayoutPart} (matched by the layout's original/template name) and creates an empty
 * slide bound to it. Merges the former {@code LayoutResolver} and {@code SlideBuilder}.
 */
@Slf4j
@ApplicationScoped
public class SlideFactory {

    /** Builds the slide part name for the given (0-based) slide index (deterministic, readable). */
    private static PartName slidePartName(int slideIndex) throws Exception {
        return new PartName("/ppt/slides/slide" + (slideIndex + 1) + ".xml");
    }

    /**
     * Resolves the {@link SlideLayoutPart} bound to a logical layout id.
     *
     * @param pptx     loaded template package
     * @param layoutId logical layout identifier
     * @param analysis template analysis describing available layouts
     * @return the matching layout part, or {@link Optional#empty()} when none matches
     */
    public Optional<SlideLayoutPart> resolveLayout(PresentationMLPackage pptx,
                                                   String layoutId,
                                                   TemplateAnalysis analysis) {
        if (analysis == null || analysis.getLayouts() == null) {
            log.warn("Template analysis or its layouts are null; cannot resolve layout {}.", layoutId);
            return Optional.empty();
        }

        Optional<LayoutAnalysis> layoutAnalysis = analysis.getLayouts().stream()
                .filter(l -> l.getLayoutId().equals(layoutId))
                .findFirst();

        if (layoutAnalysis.isEmpty()) {
            log.warn("Layout id '{}' not found in template analysis.", layoutId);
            return Optional.empty();
        }

        String originalName = layoutAnalysis.get().getOriginalName();

        for (Part part : pptx.getParts().getParts().values()) {
            if (part instanceof SlideLayoutPart layoutPart) {
                try {
                    String layoutName = layoutPart.getContents().getCSld().getName();
                    if (originalName != null && originalName.equals(layoutName)) {
                        return Optional.of(layoutPart);
                    }
                } catch (Exception e) {
                    log.debug("Unable to read name of a candidate layout part: {}", e.getMessage());
                }
            }
        }

        log.warn("No SlideLayoutPart matches original name '{}' for layout id '{}'.", originalName, layoutId);
        return Optional.empty();
    }

    /**
     * Creates a new, empty slide bound to {@code layoutPart}.
     *
     * @param pptx        loaded template package
     * @param layoutPart  layout the slide is based on
     * @param slideIndex  0-based position of the slide
     * @return the created slide part
     */
    public SlidePart createSlide(PresentationMLPackage pptx,
                                 SlideLayoutPart layoutPart,
                                 int slideIndex) throws Exception {
        SlidePart slidePart = PresentationMLPackage.createSlidePart(
                pptx.getMainPresentationPart(),
                layoutPart,
                slidePartName(slideIndex));
        log.debug("Created slide {} using layout '{}'.", slideIndex + 1, layoutPart.getPartName());
        return slidePart;
    }

    /**
     * Prepares a template for rendering by purging every existing slide while preserving
     * slide masters, layouts and the theme so freshly built slides can be attached.
     *
     * @param pptx the loaded template package
     * @throws Exception if the presentation part cannot be read
     */
    public void purgeExistingSlides(PresentationMLPackage pptx) throws Exception {
        MainPresentationPart mainPart = pptx.getMainPresentationPart();
        Presentation presentation = mainPart.getContents();

        if (presentation.getSldIdLst() == null || presentation.getSldIdLst().getSldId() == null) {
            log.info("No existing slides to purge.");
            return;
        }

        List<Presentation.SldIdLst.SldId> slidesToRemove =
                new ArrayList<>(presentation.getSldIdLst().getSldId());
        RelationshipsPart relationships = mainPart.getRelationshipsPart();

        int removedCount = 0;
        for (Presentation.SldIdLst.SldId slideId : slidesToRemove) {
            String rid = slideId.getRid();
            if (rid == null || relationships == null) {
                continue;
            }
            try {
                Relationship rel = relationships.getRelationshipByID(rid);
                if (rel == null) {
                    continue;
                }
                Part part = relationships.getPart(rel);
                if (part instanceof SlidePart) {
                    pptx.getParts().remove(part.getPartName());
                    relationships.removeRelationship(rel);
                    removedCount++;
                }
            } catch (Exception e) {
                log.warn("Failed to remove slide with rid {}: {}", rid, e.getMessage());
            }
        }

        presentation.getSldIdLst().getSldId().clear();
        log.info("Purged {} existing slide(s) from template.", removedCount);
    }
}
