package com.pptxgenerator.renderer.injection;

import com.pptxgenerator.generator.model.BoxContent;
import com.pptxgenerator.generator.model.ColumnContent;
import com.pptxgenerator.generator.model.SlideContent;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.renderer.model.RenderWarning;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.XmlUtils;
import org.docx4j.dml.CTRegularTextRun;
import org.docx4j.dml.CTTextBody;
import org.docx4j.dml.CTTextBodyProperties;
import org.docx4j.dml.CTTextCharacterProperties;
import org.docx4j.dml.CTTextListStyle;
import org.docx4j.dml.CTTextParagraph;
import org.docx4j.dml.CTTextParagraphProperties;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.pptx4j.pml.*;

import java.math.BigInteger;
import java.util.*;

/**
 * Injecte le contenu généré dans les placeholders de la slide.
 */
@Slf4j
@ApplicationScoped
public class PlaceholderInjector {

    /**
     * Injecte le contenu dans la slide.
     * Approche simple : copier les placeholders du layout dans la slide,
     * puis modifier leur texte.
     */
    public List<RenderWarning> inject(SlidePart slidePart, SlideContent content,
                                      SlideLayoutPart layoutPart, List<Zone> layoutZones) {
        List<RenderWarning> warnings = new ArrayList<>();

        try {
            // 1. Copier les placeholders du layout dans la slide
            copyLayoutPlaceholdersToSlide(slidePart, layoutPart);

            // 2. Indexer les placeholders de la slide (maintenant ils existent !)
            Map<String, Shape> placeholderMapping = indexSlidePlaceholders(slidePart, layoutZones);

            // 3. Injecter le contenu
            if (content.getTitle() != null && placeholderMapping.containsKey("title")) {
                setText(placeholderMapping.get("title"), content.getTitle());
            }

            if (content.getSubtitle() != null && placeholderMapping.containsKey("subtitle")) {
                setText(placeholderMapping.get("subtitle"), content.getSubtitle());
            }

            if (content.getBody() != null && placeholderMapping.containsKey("body")) {
                setBullets(placeholderMapping.get("body"), content.getBody().getBullets());
            }

            if (content.getLeftColumn() != null && placeholderMapping.containsKey("left_column")) {
                setColumn(placeholderMapping.get("left_column"), content.getLeftColumn());
            }

            if (content.getRightColumn() != null && placeholderMapping.containsKey("right_column")) {
                setColumn(placeholderMapping.get("right_column"), content.getRightColumn());
            }

            // Boxes
            for (int i = 1; i <= 3; i++) {
                String boxKey = "box_" + i;
                BoxContent box = getBox(content, i);
                if (box != null && placeholderMapping.containsKey(boxKey)) {
                    setBoxContent(placeholderMapping.get(boxKey), box);
                }
            }

        } catch (Exception e) {
            log.error("Erreur injection contenu", e);
            warnings.add(RenderWarning.builder()
                    .code("INJECTION_FAILED")
                    .message("Erreur lors de l'injection du contenu: " + e.getMessage())
                    .build());
        }

        return warnings;
    }

    /**
     * 🔑 CRUCIAL : Copie les placeholders du layout dans la slide
     */
    private void copyLayoutPlaceholdersToSlide(SlidePart slidePart, SlideLayoutPart layoutPart) throws Exception {
        SldLayout layout = layoutPart.getContents();
        Sld slide = slidePart.getContents();

        if (layout.getCSld() == null || layout.getCSld().getSpTree() == null) {
            return;
        }

        if (slide.getCSld() == null) {
            slide.setCSld(new CommonSlideData());
        }
        if (slide.getCSld().getSpTree() == null) {
            slide.getCSld().setSpTree(new GroupShape());
        }

        GroupShape layoutSpTree = layout.getCSld().getSpTree();
        GroupShape slideSpTree = slide.getCSld().getSpTree();

        // Copier toutes les shapes du layout qui sont des placeholders
        for (Object obj : layoutSpTree.getSpOrGrpSpOrGraphicFrame()) {
            if (!(obj instanceof Shape layoutShape)) continue;

            // Vérifier que c'est un placeholder
            if (layoutShape.getNvSpPr() == null ||
                    layoutShape.getNvSpPr().getNvPr() == null ||
                    layoutShape.getNvSpPr().getNvPr().getPh() == null) {
                continue;
            }

            // Copier la shape (deep copy)
            Shape copiedShape = XmlUtils.deepCopy(layoutShape, org.pptx4j.jaxb.Context.jcPML);

            // Ajouter à la slide
            slideSpTree.getSpOrGrpSpOrGraphicFrame().add(copiedShape);
        }

        log.debug("Placeholders copiés du layout vers la slide");
    }

    /**
     * Indexe les placeholders de la slide par zone sémantique
     */
    private Map<String, Shape> indexSlidePlaceholders(SlidePart slidePart, List<Zone> layoutZones) throws Docx4JException {
        Map<String, Shape> result = new HashMap<>();

        Sld slide = slidePart.getContents();
        if (slide.getCSld() == null || slide.getCSld().getSpTree() == null) {
            return result;
        }

        List<Shape> allPlaceholders = new ArrayList<>();
        for (Object obj : slide.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame()) {
            if (obj instanceof Shape shape) {
                if (shape.getNvSpPr() != null &&
                        shape.getNvSpPr().getNvPr() != null &&
                        shape.getNvSpPr().getNvPr().getPh() != null) {
                    allPlaceholders.add(shape);
                }
            }
        }

        // Mapper selon les zones sémantiques
        for (Zone zone : layoutZones) {
            String semanticName = zone.getSemanticName();
            if (semanticName == null) continue;

            Shape placeholder = findMatchingPlaceholder(semanticName, allPlaceholders);
            if (placeholder != null) {
                result.put(semanticName, placeholder);
            }
        }

        return result;
    }

    private Shape findMatchingPlaceholder(String semanticName, List<Shape> placeholders) {
        return switch (semanticName) {
            case "title" -> findByType(placeholders, "title", "ctrTitle");
            case "subtitle" -> findByType(placeholders, "subTitle");
            case "body" -> findLargestBody(placeholders);
            case "left_column" -> findBodyByPosition(placeholders, 0);
            case "right_column" -> findBodyByPosition(placeholders, 1);
            case "box_1" -> findBodyByPosition(placeholders, 0);
            case "box_2" -> findBodyByPosition(placeholders, 1);
            case "box_3" -> findBodyByPosition(placeholders, 2);
            case "media_placeholder" -> findByType(placeholders, "pic", "obj");
            default -> null;
        };
    }

    private Shape findByType(List<Shape> placeholders, String... types) {
        for (Shape shape : placeholders) {
            CTPlaceholder ph = shape.getNvSpPr().getNvPr().getPh();
            if (ph.getType() != null) {
                String typeValue = ph.getType().value();
                for (String type : types) {
                    if (typeValue.equals(type)) return shape;
                }
            }
        }
        return null;
    }

    private Shape findLargestBody(List<Shape> placeholders) {
        return placeholders.stream()
                .filter(s -> {
                    CTPlaceholder ph = s.getNvSpPr().getNvPr().getPh();
                    return ph.getType() == null || ph.getType().value().equals("body");
                })
                .max(Comparator.comparingLong(s ->
                        s.getSpPr().getXfrm().getExt().getCx() * s.getSpPr().getXfrm().getExt().getCy()))
                .orElse(null);
    }

    private Shape findBodyByPosition(List<Shape> placeholders, int index) {
        List<Shape> bodies = placeholders.stream()
                .filter(s -> {
                    CTPlaceholder ph = s.getNvSpPr().getNvPr().getPh();
                    return ph.getType() == null || ph.getType().value().equals("body");
                })
                .sorted(Comparator.comparingLong(s -> s.getSpPr().getXfrm().getOff().getX()))
                .toList();
        return index < bodies.size() ? bodies.get(index) : null;
    }

    private void setText(Shape shape, String text) {
        if (shape.getTxBody() == null) {
            shape.setTxBody(new CTTextBody());
        }
        CTTextBody txBody = shape.getTxBody();
        txBody.getP().clear();

        CTTextParagraph p = new CTTextParagraph();
        CTRegularTextRun run = new CTRegularTextRun();
        run.setT(text);
        p.getEGTextRun().add(run);
        txBody.getP().add(p);
    }

    private void setBullets(Shape shape, List<String> bullets) {
        if (shape.getTxBody() == null) {
            shape.setTxBody(new CTTextBody());
        }
        CTTextBody txBody = shape.getTxBody();
        txBody.getP().clear();

        for (String bullet : bullets) {
            CTTextParagraph p = new CTTextParagraph();
            CTTextParagraphProperties pPr = new CTTextParagraphProperties();
            pPr.setLvl(0);
            p.setPPr(pPr);
            CTRegularTextRun run = new CTRegularTextRun();
            run.setT(bullet);
            p.getEGTextRun().add(run);
            txBody.getP().add(p);
        }
    }

    private void setColumn(Shape shape, ColumnContent column) {
        if (shape.getTxBody() == null) {
            shape.setTxBody(new CTTextBody());
        }
        CTTextBody txBody = shape.getTxBody();
        txBody.getP().clear();

        if (column.getHeader() != null) {
            CTTextParagraph p = new CTTextParagraph();
            CTRegularTextRun run = new CTRegularTextRun();
            run.setT(column.getHeader());
            CTTextCharacterProperties rPr = new CTTextCharacterProperties();
            rPr.setB(true);
            run.setRPr(rPr);
            p.getEGTextRun().add(run);
            txBody.getP().add(p);
        }

        if (column.getBullets() != null) {
            for (String bullet : column.getBullets()) {
                CTTextParagraph p = new CTTextParagraph();
                CTTextParagraphProperties pPr = new CTTextParagraphProperties();
                pPr.setLvl(1);
                p.setPPr(pPr);
                CTRegularTextRun run = new CTRegularTextRun();
                run.setT(bullet);
                p.getEGTextRun().add(run);
                txBody.getP().add(p);
            }
        }
    }

    private void setBoxContent(Shape shape, BoxContent box) {
        if (shape.getTxBody() == null) {
            shape.setTxBody(new CTTextBody());
        }
        CTTextBody txBody = shape.getTxBody();
        txBody.getP().clear();

        CTTextParagraph metricP = new CTTextParagraph();
        CTRegularTextRun metricRun = new CTRegularTextRun();
        metricRun.setT(box.getMetric());
        metricP.getEGTextRun().add(metricRun);
        txBody.getP().add(metricP);

        CTTextParagraph labelP = new CTTextParagraph();
        CTRegularTextRun labelRun = new CTRegularTextRun();
        labelRun.setT(box.getLabel());
        labelP.getEGTextRun().add(labelRun);
        txBody.getP().add(labelP);
    }

    private BoxContent getBox(SlideContent content, int index) {
        return switch (index) {
            case 1 -> content.getBox1();
            case 2 -> content.getBox2();
            case 3 -> content.getBox3();
            default -> null;
        };
    }
}