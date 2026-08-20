package com.pptxgenerator.renderer.injection;

import com.pptxgenerator.generator.model.BoxContent;
import com.pptxgenerator.generator.model.ColumnContent;
import com.pptxgenerator.generator.model.SlideContent;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.renderer.model.RenderWarning;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.XmlUtils;
import org.docx4j.dml.*;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.pptx4j.pml.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@ApplicationScoped
public class PlaceholderInjector {

    /**
     * Injecte le contenu dans la slide en clonant les placeholders du layout.
     *
     * @param slidePart      La nouvelle slide vide (liée au layout)
     * @param content        Le contenu généré par l'IA pour cette slide
     * @param layoutPart     Le layout d'origine (source des placeholders)
     * @param layoutZones    La liste des zones analysées (pour mapper semanticName)
     * @return               Liste des warnings générés (ex: contenu tronqué)
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

            // Compteur local pour garantir l'unicité des IDs dans cette slide (Thread-safe)
            long shapeIdCounter = 1000L;

            for (Object obj : layout.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame()) {
                if (!(obj instanceof Shape layoutShape)) continue;
                if (layoutShape.getNvSpPr() == null || layoutShape.getNvSpPr().getNvPr() == null) continue;

                CTPlaceholder ph = layoutShape.getNvSpPr().getNvPr().getPh();
                if (ph == null) continue; // On ne clone que les placeholders

                String phType = ph.getType() != null ? ph.getType().value() : "body";

                // Trouver le nom sémantique de cette zone (ex: "left_column", "box_1")
                String semanticName = findSemanticName(layoutShape, layoutZones, ph);

                // 🔑 DEEP COPY de la shape du layout
                Shape clonedShape = (Shape) XmlUtils.deepCopy(layoutShape, org.pptx4j.jaxb.Context.jcPML);

                // Assigner un ID unique
                clonedShape.getNvSpPr().getCNvPr().setId(shapeIdCounter);
                clonedShape.getNvSpPr().getCNvPr().setName("Clone_" + phType + "_" + shapeIdCounter);
                shapeIdCounter++;

                // Injecter le contenu selon le type et le nom sémantique
                if (clonedShape.getTxBody() != null) {
                    clonedShape.getTxBody().getP().clear(); // Vider le texte "Cliquez pour..."

                    if ("title".equals(phType) || "ctrTitle".equals(phType)) {
                        if (content.getTitle() != null) {
                            addParagraph(clonedShape, content.getTitle(), false, 0);
                        }
                    }
                    else if ("subTitle".equals(phType)) {
                        if (content.getSubtitle() != null) {
                            addParagraph(clonedShape, content.getSubtitle(), false, 0);
                        }
                    }
                    else if ("body".equals(phType) || "obj".equals(phType)) {
                        // Gestion des colonnes et boxes via le semanticName
                        if ("left_column".equals(semanticName) && content.getLeftColumn() != null) {
                            injectColumn(clonedShape, content.getLeftColumn());
                        }
                        else if ("right_column".equals(semanticName) && content.getRightColumn() != null) {
                            injectColumn(clonedShape, content.getRightColumn());
                        }
                        else if (semanticName != null && semanticName.startsWith("box_") && content.getBox1() != null) {
                            // Simplification : on mappe box_1, box_2, box_3 dynamiquement
                            injectBox(clonedShape, getBoxByIndex(content, semanticName));
                        }
                        else if (content.getBody() != null && content.getBody().getBullets() != null) {
                            // Fallback sur le body standard
                            injectBullets(clonedShape, content.getBody().getBullets());
                        }
                    }
                    // 🔑 GESTION DES MÉDIAS : On ne touche pas au texte, l'icône native reste
                    else if ("pic".equals(phType) || "chart".equals(phType) || "tbl".equals(phType)) {
                        log.debug("Zone média {} clonée (vide, icône native préservée)", phType);
                    }
                }

                // Ajouter la shape clonée à la slide
                slideSpTree.getSpOrGrpSpOrGraphicFrame().add(clonedShape);
            }

        } catch (Exception e) {
            log.error("Erreur lors de l'injection du contenu", e);
            warnings.add(RenderWarning.builder()
                .code("INJECTION_FAILED")
                .message("Erreur interne lors du rendu: " + e.getMessage())
                .build());
        }

        return warnings;
    }

    // ============================================================
    // MÉTHODES D'INJECTION SPÉCIFIQUES
    // ============================================================

    private void injectBullets(Shape shape, List<String> bullets) {
        for (String bullet : bullets) {
            addParagraph(shape, bullet, true, 0);
        }
    }

    private void injectColumn(Shape shape, ColumnContent column) {
        if (column.getHeader() != null) {
            CTTextParagraph p = new CTTextParagraph();
            CTRegularTextRun run = new CTRegularTextRun();
            run.setT(column.getHeader());
            CTTextCharacterProperties rPr = new CTTextCharacterProperties();
            rPr.setB(true); // Gras pour le header
            run.setRPr(rPr);
            p.getEGTextRun().add(run);
            shape.getTxBody().getP().add(p);
        }
        if (column.getBullets() != null) {
            for (String bullet : column.getBullets()) {
                addParagraph(shape, bullet, true, 1); // Niveau 1 pour décalage sous le header
            }
        }
    }

    private void injectBox(Shape shape, BoxContent box) {
        if (box == null) return;

        // Métrique (grande police)
        CTTextParagraph p1 = new CTTextParagraph();
        CTRegularTextRun r1 = new CTRegularTextRun();
        r1.setT(box.getMetric());
        CTTextCharacterProperties rPr1 = new CTTextCharacterProperties();
        rPr1.setSz(4800); // 48pt
        r1.setRPr(rPr1);
        p1.getEGTextRun().add(r1);
        shape.getTxBody().getP().add(p1);

        // Label (petite police)
        CTTextParagraph p2 = new CTTextParagraph();
        CTRegularTextRun r2 = new CTRegularTextRun();
        r2.setT(box.getLabel());
        CTTextCharacterProperties rPr2 = new CTTextCharacterProperties();
        rPr2.setSz(1400); // 14pt
        r2.setRPr(rPr2);
        p2.getEGTextRun().add(r2);
        shape.getTxBody().getP().add(p2);
    }

    /**
     * Ajoute un paragraphe (avec ou sans puce) à une shape
     */
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

    // ============================================================
    // MÉTHODES UTILITAIRES DE MAPPING
    // ============================================================

    /**
     * Retrouve le semanticName (ex: "left_column") associé à une shape de layout.
     * On se base sur l'idx du placeholder pour faire le lien avec la Zone analysée.
     */
    private String findSemanticName(Shape layoutShape, List<Zone> layoutZones, CTPlaceholder ph) {
        if (layoutZones == null || layoutZones.isEmpty()) return null;

        for (Zone zone : layoutZones) {
            // Simplification : si c'est le premier body qu'on trouve et qu'on cherche left_column
            if (zone.getSemanticName() != null) {
                if ("body".equals(ph.getType() != null ? ph.getType().value() : "body") && "left_column".equals(zone.getSemanticName())) {
                    return "left_column";
                }
                if ("body".equals(ph.getType() != null ? ph.getType().value() : "body") && "right_column".equals(zone.getSemanticName())) {
                    return "right_column";
                }
                // Pour les boxes, on matche par indice ou ordre
                if (zone.getSemanticName().startsWith("box_")) {
                    return zone.getSemanticName();
                }
            }
        }
        return null;
    }

    private BoxContent getBoxByIndex(SlideContent content, String semanticName) {
        return switch (semanticName) {
            case "box_1" -> content.getBox1();
            case "box_2" -> content.getBox2();
            case "box_3" -> content.getBox3();
            default -> null;
        };
    }
}
