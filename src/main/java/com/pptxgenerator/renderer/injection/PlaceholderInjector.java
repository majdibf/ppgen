package com.pptxgenerator.renderer.injection;


import com.pptxgenerator.generator.model.BoxContent;
import com.pptxgenerator.generator.model.ColumnContent;
import com.pptxgenerator.generator.model.SlideContent;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.renderer.model.RenderWarning;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.XmlUtils;
import org.docx4j.dml.*;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.pptx4j.pml.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@ApplicationScoped
public class PlaceholderInjector {

    @Inject
    PlaceholderMapper mapper;

    /**
     * Injecte le contenu dans la slide.
     * 1. Clone tous les placeholders du layout vers la slide (pour un XML valide).
     * 2. Utilise le Mapper pour trouver quelle shape correspond à quel semanticName.
     * 3. Injecte le contenu spécifique dans chaque shape mappée.
     */
    public List<RenderWarning> inject(SlidePart slidePart, SlideContent content,
                                      SlideLayoutPart layoutPart, List<Zone> layoutZones) {
        List<RenderWarning> warnings = new ArrayList<>();

        try {
            SldLayout layout = layoutPart.getContents();
            if (layout.getCSld() == null || layout.getCSld().getSpTree() == null) {
                return warnings;
            }

            Sld slide = slidePart.getContents();
            if (slide.getCSld() == null) slide.setCSld(new CommonSlideData());
            if (slide.getCSld().getSpTree() == null) slide.getCSld().setSpTree(new GroupShape());

            GroupShape slideSpTree = slide.getCSld().getSpTree();
            long shapeIdCounter = 1000L;

            // ÉTAPE 1 : Cloner TOUS les placeholders du layout vers la slide
            for (Object obj : layout.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame()) {
                if (!(obj instanceof Shape layoutShape)) continue;
                if (layoutShape.getNvSpPr() == null || layoutShape.getNvSpPr().getNvPr() == null) continue;
                if (layoutShape.getNvSpPr().getNvPr().getPh() == null) continue; // On ne clone que les placeholders

                // Deep Copy pour préserver la structure XML valide
                Shape clonedShape = (Shape) XmlUtils.deepCopy(layoutShape, org.pptx4j.jaxb.Context.jcPML);

                // ID unique pour éviter les conflits
                clonedShape.getNvSpPr().getCNvPr().setId(shapeIdCounter);
                clonedShape.getNvSpPr().getCNvPr().setName("Clone_" + shapeIdCounter);
                shapeIdCounter++;

                // Ajouter à la slide
                slideSpTree.getSpOrGrpSpOrGraphicFrame().add(clonedShape);
            }

            // ÉTAPE 2 : Mapper les placeholders de la slide (maintenant peuplée) vers les noms sémantiques
            Map<String, Shape> mapping = mapper.mapPlaceholders(slidePart, layoutZones);
            log.debug("Mapping des placeholders effectué : {}", mapping.keySet());

            // ÉTAPE 3 : Injecter le contenu dans les shapes mappées
            for (Map.Entry<String, Shape> entry : mapping.entrySet()) {
                String semanticName = entry.getKey();
                Shape shape = entry.getValue();

                if (shape.getTxBody() != null) {
                    shape.getTxBody().getP().clear(); // Vider le texte "Cliquez pour..."
                }

                injectContentBySemanticName(shape, semanticName, content);
            }

        } catch (Docx4JException e) {
            log.error("Erreur Docx4J lors du mapping ou de l'injection", e);
            warnings.add(RenderWarning.builder().code("INJECTION_FAILED").message(e.getMessage()).build());
        } catch (Exception e) {
            log.error("Erreur inattendue lors de l'injection", e);
            warnings.add(RenderWarning.builder().code("INJECTION_FAILED").message(e.getMessage()).build());
        }

        return warnings;
    }

    private void injectContentBySemanticName(Shape shape, String semanticName, SlideContent content) {
        if (shape.getTxBody() == null) return;

        switch (semanticName) {
            case "title" -> {
                if (content.getTitle() != null) addParagraph(shape, content.getTitle(), false, 0);
            }
            case "subtitle" -> {
                if (content.getSubtitle() != null) addParagraph(shape, content.getSubtitle(), false, 0);
            }
            case "body" -> {
                if (content.getBody() != null && content.getBody().getBullets() != null) {
                    injectBullets(shape, content.getBody().getBullets());
                }
            }
            case "left_column" -> {
                if (content.getLeftColumn() != null) injectColumn(shape, content.getLeftColumn());
            }
            case "right_column" -> {
                if (content.getRightColumn() != null) injectColumn(shape, content.getRightColumn());
            }
            case "box_1" -> injectBox(shape, content.getBox1());
            case "box_2" -> injectBox(shape, content.getBox2());
            case "box_3" -> injectBox(shape, content.getBox3());
            case "media_placeholder" -> {
                // 🔑 MÉDIAS : On ne touche pas au texte, l'icône native reste
                log.debug("Zone média clonée et préservée (icône native)");
            }
            default -> log.debug("Aucun contenu à injecter pour : {}", semanticName);
        }
    }

    private void injectBullets(Shape shape, List<String> bullets) {
        for (String bullet : bullets) addParagraph(shape, bullet, true, 0);
    }

    private void injectColumn(Shape shape, ColumnContent column) {
        if (column.getHeader() != null) {
            CTTextParagraph p = new CTTextParagraph();
            CTRegularTextRun run = new CTRegularTextRun();
            run.setT(column.getHeader());
            CTTextCharacterProperties rPr = new CTTextCharacterProperties();
            rPr.setB(true);
            run.setRPr(rPr);
            p.getEGTextRun().add(run);
            shape.getTxBody().getP().add(p);
        }
        if (column.getBullets() != null) {
            for (String bullet : column.getBullets()) addParagraph(shape, bullet, true, 1);
        }
    }

    private void injectBox(Shape shape, BoxContent box) {
        if (box == null) return;

        CTTextParagraph p1 = new CTTextParagraph();
        CTRegularTextRun r1 = new CTRegularTextRun();
        r1.setT(box.getMetric());
        CTTextCharacterProperties rPr1 = new CTTextCharacterProperties();
        rPr1.setSz(4800); // 48pt
        r1.setRPr(rPr1);
        p1.getEGTextRun().add(r1);
        shape.getTxBody().getP().add(p1);

        CTTextParagraph p2 = new CTTextParagraph();
        CTRegularTextRun r2 = new CTRegularTextRun();
        r2.setT(box.getLabel());
        CTTextCharacterProperties rPr2 = new CTTextCharacterProperties();
        rPr2.setSz(1400); // 14pt
        r2.setRPr(rPr2);
        p2.getEGTextRun().add(r2);
        shape.getTxBody().getP().add(p2);
    }

    private void addParagraph(Shape shape, String text, boolean isBullet, int level) {
        if (text == null || text.isBlank()) return;
        CTTextParagraph p = new CTTextParagraph();
        if (isBullet) {
            CTTextParagraphProperties pPr = new CTTextParagraphProperties();
            pPr.setLvl(level);
            p.setPPr(pPr);
        }
        CTRegularTextRun run = new CTRegularTextRun();
        run.setT(text);
        p.getEGTextRun().add(run);
        shape.getTxBody().getP().add(p);
    }
}