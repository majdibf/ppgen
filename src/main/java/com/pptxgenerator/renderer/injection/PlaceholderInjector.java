package com.pptxgenerator.renderer.injection;

import com.pptxgenerator.generator.model.BoxContent;
import com.pptxgenerator.generator.model.ColumnContent;
import com.pptxgenerator.generator.model.SlideContent;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.renderer.model.RenderWarning;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.dml.CTRegularTextRun;
import org.docx4j.dml.CTTextBody;
import org.docx4j.dml.CTTextBodyProperties;
import org.docx4j.dml.CTTextCharacterProperties;
import org.docx4j.dml.CTTextListStyle;
import org.docx4j.dml.CTTextParagraph;
import org.docx4j.dml.CTTextParagraphProperties;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.pptx4j.pml.Shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Injecte le contenu généré dans les placeholders de la slide.
 */
@Slf4j
@ApplicationScoped
public class PlaceholderInjector {

    private static final int MAX_CHARS_PER_PLACEHOLDER = 500;

    /**
     * Injecte le contenu dans les placeholders.
     */
    public List<RenderWarning> inject(SlidePart slidePart, SlideContent content,
                                       Map<String, Shape> placeholderMapping, List<Zone> layoutZones) {
        List<RenderWarning> warnings = new ArrayList<>();

        // Titre
        if (content.getTitle() != null && placeholderMapping.containsKey("title")) {
            Shape titlePlaceholder = placeholderMapping.get("title");
            boolean truncated = setText(titlePlaceholder, content.getTitle());
            if (truncated) {
                warnings.add(RenderWarning.builder()
                    .code("CONTENT_TRUNCATED")
                    .message("Titre tronqué pour respecter la capacité du placeholder")
                    .build());
            }
        }

        // Sous-titre
        if (content.getSubtitle() != null && placeholderMapping.containsKey("subtitle")) {
            Shape subtitlePlaceholder = placeholderMapping.get("subtitle");
            setText(subtitlePlaceholder, content.getSubtitle());
        }

        // Body
        if (content.getBody() != null && placeholderMapping.containsKey("body")) {
            Shape bodyPlaceholder = placeholderMapping.get("body");
            setBullets(bodyPlaceholder, content.getBody().getBullets());
        }

        // Left column
        if (content.getLeftColumn() != null && placeholderMapping.containsKey("left_column")) {
            Shape leftPlaceholder = placeholderMapping.get("left_column");
            setColumn(leftPlaceholder, content.getLeftColumn());
        }

        // Right column
        if (content.getRightColumn() != null && placeholderMapping.containsKey("right_column")) {
            Shape rightPlaceholder = placeholderMapping.get("right_column");
            setColumn(rightPlaceholder, content.getRightColumn());
        }

        // Boxes
        for (int i = 1; i <= 3; i++) {
            String boxKey = "box_" + i;
            BoxContent box = getBox(content, i);
            if (box != null && placeholderMapping.containsKey(boxKey)) {
                Shape boxPlaceholder = placeholderMapping.get(boxKey);
                setBoxContent(boxPlaceholder, box);
            }
        }

        // Media description (laissé vide en V1, juste log)
        if (content.getMediaDescription() != null) {
            log.debug("Media description ignorée en V1: {}", content.getMediaDescription());
        }

        return warnings;
    }

    /**
     * Définit le texte d'un placeholder (une seule ligne).
     */
    private boolean setText(Shape placeholder, String text) {
        CTTextBody txBody = ensureTextBody(placeholder);
        txBody.getP().clear();

        // Tronquer si nécessaire (règle P3)
        if (text.length() > MAX_CHARS_PER_PLACEHOLDER) {
            text = text.substring(0, MAX_CHARS_PER_PLACEHOLDER - 3) + "...";
            return true;
        }

        CTTextParagraph paragraph = new CTTextParagraph();
        CTRegularTextRun run = new CTRegularTextRun();
        run.setT(text);
        paragraph.getEGTextRun().add(run);
        txBody.getP().add(paragraph);

        return false;
    }

    /**
     * Définit les bullets d'un placeholder.
     */
    private void setBullets(Shape placeholder, List<String> bullets) {
        CTTextBody txBody = ensureTextBody(placeholder);
        txBody.getP().clear();

        for (String bullet : bullets) {
            CTTextParagraph paragraph = new CTTextParagraph();
            CTTextParagraphProperties pPr = new CTTextParagraphProperties();
            pPr.setLvl(0);
            paragraph.setPPr(pPr);

            CTRegularTextRun run = new CTRegularTextRun();
            run.setT(bullet);
            paragraph.getEGTextRun().add(run);
            txBody.getP().add(paragraph);
        }
    }

    /**
     * Définit le contenu d'une colonne (header + bullets).
     */
    private void setColumn(Shape placeholder, ColumnContent column) {
        CTTextBody txBody = ensureTextBody(placeholder);
        txBody.getP().clear();

        // Header (en gras)
        if (column.getHeader() != null) {
            CTTextParagraph headerParagraph = new CTTextParagraph();
            CTRegularTextRun headerRun = new CTRegularTextRun();
            headerRun.setT(column.getHeader());
            CTTextCharacterProperties rPr = new CTTextCharacterProperties();
            rPr.setB(true);
            headerRun.setRPr(rPr);
            headerParagraph.getEGTextRun().add(headerRun);
            txBody.getP().add(headerParagraph);
        }

        // Bullets
        if (column.getBullets() != null) {
            for (String bullet : column.getBullets()) {
                CTTextParagraph paragraph = new CTTextParagraph();
                CTTextParagraphProperties pPr = new CTTextParagraphProperties();
                pPr.setLvl(1);
                paragraph.setPPr(pPr);

                CTRegularTextRun run = new CTRegularTextRun();
                run.setT(bullet);
                paragraph.getEGTextRun().add(run);
                txBody.getP().add(paragraph);
            }
        }
    }

    /**
     * Définit le contenu d'une box (metric + label).
     */
    private void setBoxContent(Shape placeholder, BoxContent box) {
        CTTextBody txBody = ensureTextBody(placeholder);
        txBody.getP().clear();

        // Metric (grand)
        CTTextParagraph metricParagraph = new CTTextParagraph();
        CTRegularTextRun metricRun = new CTRegularTextRun();
        metricRun.setT(box.getMetric());
        CTTextCharacterProperties metricRPr = new CTTextCharacterProperties();
        metricRPr.setSz(4800); // 48pt
        metricRun.setRPr(metricRPr);
        metricParagraph.getEGTextRun().add(metricRun);
        txBody.getP().add(metricParagraph);

        // Label (petit)
        CTTextParagraph labelParagraph = new CTTextParagraph();
        CTRegularTextRun labelRun = new CTRegularTextRun();
        labelRun.setT(box.getLabel());
        CTTextCharacterProperties labelRPr = new CTTextCharacterProperties();
        labelRPr.setSz(1400); // 14pt
        labelRun.setRPr(labelRPr);
        labelParagraph.getEGTextRun().add(labelRun);
        txBody.getP().add(labelParagraph);
    }

    /**
     * S'assure que le placeholder a un txBody.
     */
    private CTTextBody ensureTextBody(Shape placeholder) {
        if (placeholder.getTxBody() == null) {
            CTTextBody txBody = new CTTextBody();
            txBody.setBodyPr(new CTTextBodyProperties());
            txBody.setLstStyle(new CTTextListStyle());
            placeholder.setTxBody(txBody);
        }
        return placeholder.getTxBody();
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
