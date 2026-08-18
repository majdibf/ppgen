package com.pptxgenerator.renderer;

import com.pptxgenerator.model.ContentMap;
import com.pptxgenerator.model.EnrichedPlan;
import com.pptxgenerator.model.SlideContent;
import com.pptxgenerator.model.SlideLayout;
import com.pptxgenerator.model.TemplateStructure;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.PresentationML.MainPresentationPart;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart;
import org.pptx4j.pml.Presentation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Experimental renderer using layout placeholder inheritance.
 *
 * <p>This class is intentionally not injected and is not used by the main
 * pipeline. It exists to compare placeholder-only rendering with the current
 * renderer without changing production behavior.</p>
 */
public class InheritedPlaceholderPresentationRenderer {

    private final InheritedPlaceholderRenderer placeholderRenderer =
        new InheritedPlaceholderRenderer();

    public File render(String templatePath, TemplateStructure template,
                       EnrichedPlan plan, ContentMap contentMap,
                       String outputPath) throws Exception {
        PresentationMLPackage presentation =
            PresentationMLPackage.load(new File(templatePath));
        MainPresentationPart mainPart = presentation.getMainPresentationPart();

        removeExistingSlides(presentation, mainPart);

        for (int index = 0; index < plan.getSlides().size(); index++) {
            var enrichedSlide = plan.getSlides().get(index);
            SlideLayout layout = enrichedSlide.getAssignedLayout();
            if (layout == null) continue;

            SlideLayoutPart layoutPart = findLayout(presentation, layout);
            if (layoutPart == null) continue;

            PartName slideName = new PartName(
                "/ppt/slides/slide" + (index + 1) + ".xml");
            SlidePart slidePart = PresentationMLPackage.createSlidePart(
                mainPart, layoutPart, slideName);

            SlideContent content = contentMap.getSlideContent("slide_" + index);
            if (content != null && content.getZoneContents() != null) {
                placeholderRenderer.addTextPlaceholders(
                    slidePart, layoutPart, layout, content.getZoneContents());
            }
            // createSlidePart registers the slide relationship in the package.
        }

        File output = new File(outputPath);
        presentation.save(output);
        return output;
    }

    private SlideLayoutPart findLayout(PresentationMLPackage presentation,
                                       SlideLayout layout) {
        return (SlideLayoutPart) presentation.getParts().getParts().values().stream()
            .filter(part -> part instanceof SlideLayoutPart)
            .filter(part -> part.getPartName().toString().equals(layout.getOriginalName()))
            .findFirst()
            .orElse(null);
    }

    private void removeExistingSlides(PresentationMLPackage presentation,
                                      MainPresentationPart mainPart) throws Exception {
        Presentation contents = mainPart.getContents();
        if (contents.getSldIdLst() == null || contents.getSldIdLst().getSldId() == null) {
            return;
        }

        List<Presentation.SldIdLst.SldId> existing =
            new ArrayList<>(contents.getSldIdLst().getSldId());
        RelationshipsPart relationships = mainPart.getRelationshipsPart();

        for (Presentation.SldIdLst.SldId slideId : existing) {
            if (slideId.getRid() == null) continue;
            org.docx4j.relationships.Relationship relationship =
                relationships.getRelationshipByID(slideId.getRid());
            if (relationship == null) continue;
            Part part = relationships.getPart(relationship);
            if (part instanceof SlidePart) {
                presentation.getParts().remove(part.getPartName());
                relationships.removeRelationship(relationship);
            }
        }
        contents.getSldIdLst().getSldId().clear();
    }
}
