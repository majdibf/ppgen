package com.pptxgenerator.renderer.preparation;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.PresentationML.MainPresentationPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart;
import org.pptx4j.pml.Presentation;

import java.util.ArrayList;
import java.util.List;

/**
 * Prépare le template en supprimant toutes les slides existantes
 * tout en conservant les Slide Masters, Layouts et le Thème.
 */
@Slf4j
@ApplicationScoped
public class TemplatePreparator {

    /**
     * Supprime toutes les slides du template.
     */
    public void purgeExistingSlides(PresentationMLPackage pptx) throws Docx4JException {
        log.info("Purge des slides existantes du template...");

        MainPresentationPart mainPart = pptx.getMainPresentationPart();
        Presentation presentation = mainPart.getContents();

        if (presentation.getSldIdLst() == null || presentation.getSldIdLst().getSldId() == null) {
            log.info("Aucune slide à supprimer");
            return;
        }

        List<Presentation.SldIdLst.SldId> slidesToRemove = new ArrayList<>(presentation.getSldIdLst().getSldId());
        RelationshipsPart relationships = mainPart.getRelationshipsPart();

        int removedCount = 0;
        for (Presentation.SldIdLst.SldId slideId : slidesToRemove) {
            if (slideId.getRid() == null) continue;

            org.docx4j.relationships.Relationship relationship = relationships.getRelationshipByID(slideId.getRid());
            if (relationship == null) continue;

            Part part = relationships.getPart(relationship);
            if (part instanceof SlidePart) {
                pptx.getParts().remove(part.getPartName());
                relationships.removeRelationship(relationship);
                removedCount++;
            }
        }

        presentation.getSldIdLst().getSldId().clear();
        log.info("{} slides supprimées du template", removedCount);
    }
}
