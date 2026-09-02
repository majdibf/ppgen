package com.pptxgenerator.pipeline.renderer;

import lombok.extern.slf4j.Slf4j;
import org.docx4j.XmlUtils;
import org.docx4j.dml.CTRegularTextRun;
import org.docx4j.dml.CTTextParagraph;
import org.docx4j.dml.CTTextParagraphProperties;
import org.docx4j.dml.STTextAlignType;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.pptx4j.jaxb.Context;
import org.pptx4j.pml.CTPlaceholder;
import org.pptx4j.pml.GroupShape;
import org.pptx4j.pml.Shape;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper unique pour toutes les opérations OOXML.
 * Centralise tous les appels à docx4j pour isoler la complexité.
 */
@Slf4j
@ApplicationScoped
public class OoxmlHelper {

    // === PLACEHOLDER EXTRACTION ===

    /**
     * Extrait toutes les formes qui ont un placeholder ({@code <p:ph>}) d'un arbre de formes.
     */
    public List<Shape> extractPlaceholders(GroupShape spTree) {
        List<Shape> placeholders = new ArrayList<>();
        if (spTree == null) {
            return placeholders;
        }

        for (Object obj : spTree.getSpOrGrpSpOrGraphicFrame()) {
            if (obj instanceof Shape shape && isPlaceholder(shape)) {
                placeholders.add(shape);
            }
        }
        return placeholders;
    }

    /**
     * Vérifie si une forme est un placeholder.
     */
    public boolean isPlaceholder(Shape shape) {
        return shape.getNvSpPr() != null
                && shape.getNvSpPr().getNvPr() != null
                && shape.getNvSpPr().getNvPr().getPh() != null;
    }

    /**
     * Récupère le CTPlaceholder d'une forme, ou null si ce n'est pas un placeholder.
     */
    public CTPlaceholder getPlaceholder(Shape shape) {
        if (shape.getNvSpPr() == null || shape.getNvSpPr().getNvPr() == null) {
            return null;
        }
        return shape.getNvSpPr().getNvPr().getPh();
    }

    // === PROPRIÉTÉS DES FORMES ===

    /**
     * Récupère l'index OOXML du placeholder.
     */
    public long getId(Shape shape) {
        CTPlaceholder ph = getPlaceholder(shape);
        return ph == null ? -1L : ph.getIdx();
    }

    /**
     * Récupère le type OOXML du placeholder (title, body, pic, etc.).
     */
    public String getType(Shape shape) {
        CTPlaceholder ph = getPlaceholder(shape);
        return ph != null && ph.getType() != null ? ph.getType().value() : null;
    }

    /**
     * Récupère la position X de la forme.
     */
    public long getX(Shape shape) {
        return hasGeometry(shape) ? shape.getSpPr().getXfrm().getOff().getX() : 0L;
    }

    /**
     * Récupère la position Y de la forme.
     */
    public long getY(Shape shape) {
        return hasGeometry(shape) ? shape.getSpPr().getXfrm().getOff().getY() : 0L;
    }

    /**
     * Récupère la largeur de la forme.
     */
    public long getWidth(Shape shape) {
        return hasGeometry(shape) ? shape.getSpPr().getXfrm().getExt().getCx() : 0L;
    }

    /**
     * Récupère la hauteur de la forme.
     */
    public long getHeight(Shape shape) {
        return hasGeometry(shape) ? shape.getSpPr().getXfrm().getExt().getCy() : 0L;
    }

    /**
     * Vérifie si la forme a une géométrie explicite (position + dimensions).
     */
    public boolean hasGeometry(Shape shape) {
        return shape.getSpPr() != null
                && shape.getSpPr().getXfrm() != null
                && shape.getSpPr().getXfrm().getOff() != null
                && shape.getSpPr().getXfrm().getExt() != null;
    }

    // === CLONAGE DE FORMES ===

    /**
     * Clone une forme en profondeur avec un nouvel ID.
     */
    public Shape cloneShape(Shape original, long newId) {
        Shape cloned = (Shape) XmlUtils.deepCopy(original, Context.jcPML);
        cloned.getNvSpPr().getCNvPr().setId(newId);
        cloned.getNvSpPr().getCNvPr().setName("Placeholder_" + newId);
        return cloned;
    }

    // === OPÉRATIONS SUR LE TEXTE ===

    /**
     * Efface tout le texte d'une forme.
     */
    public void clearText(Shape shape) {
        if (shape.getTxBody() != null) {
            shape.getTxBody().getP().clear();
        }
    }

    /**
     * Ajoute un paragraphe à une forme avec le style spécifié.
     */
    public void addParagraph(Shape shape, String text, ParagraphStyle style) {
        if (shape.getTxBody() == null || text == null || text.isBlank()) {
            return;
        }

        CTTextParagraph p = new CTTextParagraph();

        if (style != null && style.hasProperties()) {
            CTTextParagraphProperties pPr = new CTTextParagraphProperties();
            if (style.isBullet()) {
                pPr.setLvl(style.getLevel());
            }
            if (style.isCentered()) {
                pPr.setAlgn(STTextAlignType.CTR);
            }
            if (style.getAlignment() != null) {
                pPr.setAlgn(style.getAlignment());
            }
            p.setPPr(pPr);
        }

        CTRegularTextRun run = new CTRegularTextRun();
        run.setT(text);
        p.getEGTextRun().add(run);
        shape.getTxBody().getP().add(p);
    }

    // === INJECTION DE TEXTE STRUCTURÉ ===

    /**
     * Injecte un corps de texte avec gestion des paragraphes et des puces.
     */
    public void injectBodyText(Shape shape, String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        if (text.contains("\n\n")) {
            // Double saut de ligne = séparation de paragraphes
            for (String paragraph : text.split("\n\n")) {
                injectBulletList(shape, paragraph.split("\n"));
            }
        } else if (text.contains("\n")) {
            // Simple saut de ligne = liste à puces
            injectBulletList(shape, text.split("\n"));
        } else {
            // Texte simple
            addParagraph(shape, text, ParagraphStyle.simple());
        }
    }

    /**
     * Injecte une liste à puces.
     */
    public void injectBulletList(Shape shape, String[] lines) {
        for (String line : lines) {
            line = line.trim();
            if (line.isBlank()) {
                continue;
            }

            ParagraphStyle style = parseBulletStyle(line);
            String text = style.isBullet() ? line.substring(style.getPrefixLength()) : line;
            addParagraph(shape, text, style);
        }
    }

    /**
     * Parse une ligne pour détecter si c'est une puce et à quel niveau.
     */
    private ParagraphStyle parseBulletStyle(String line) {
        if (line.startsWith("  - ") || line.startsWith("  • ")) {
            return ParagraphStyle.bullet(1, 4);
        }
        if (line.startsWith("- ") || line.startsWith("• ")) {
            return ParagraphStyle.bullet(0, 2);
        }
        return ParagraphStyle.simple();
    }

    // === STYLE DE PARAGRAPHE (inner class) ===

    /**
     * Style de paragraphe pour l'injection de texte.
     * Utilise le pattern Builder pour une construction fluide.
     */
    public static class ParagraphStyle {
        private final boolean bullet;
        private final int level;
        private final boolean centered;
        private final STTextAlignType alignment;
        private final int prefixLength;

        private ParagraphStyle(boolean bullet, int level, boolean centered,
                               STTextAlignType alignment, int prefixLength) {
            this.bullet = bullet;
            this.level = level;
            this.centered = centered;
            this.alignment = alignment;
            this.prefixLength = prefixLength;
        }

        /**
         * Style simple : texte brut sans mise en forme.
         */
        public static ParagraphStyle simple() {
            return new ParagraphStyle(false, 0, false, null, 0);
        }

        /**
         * Style avec puce au niveau spécifié.
         */
        public static ParagraphStyle bullet(int level, int prefixLength) {
            return new ParagraphStyle(true, level, false, null, prefixLength);
        }

        /**
         * Style centré.
         */
        public static ParagraphStyle centered() {
            return new ParagraphStyle(false, 0, true, STTextAlignType.CTR, 0);
        }

        /**
         * Style avec alignement personnalisé.
         */
        public static ParagraphStyle withAlignment(STTextAlignType alignment) {
            return new ParagraphStyle(false, 0, false, alignment, 0);
        }

        /**
         * Builder pour une construction fluide.
         */
        public static Builder builder() {
            return new Builder();
        }

        public boolean hasProperties() {
            return bullet || centered || alignment != null;
        }

        // Getters
        public boolean isBullet() { return bullet; }
        public int getLevel() { return level; }
        public boolean isCentered() { return centered; }
        public STTextAlignType getAlignment() { return alignment; }
        public int getPrefixLength() { return prefixLength; }

        /**
         * Builder interne pour ParagraphStyle.
         */
        public static class Builder {
            private boolean bullet;
            private int level;
            private boolean centered;
            private STTextAlignType alignment;
            private int prefixLength;

            public Builder bullet(boolean bullet) {
                this.bullet = bullet;
                return this;
            }

            public Builder level(int level) {
                this.level = level;
                return this;
            }

            public Builder centered(boolean centered) {
                this.centered = centered;
                return this;
            }

            public Builder alignment(STTextAlignType alignment) {
                this.alignment = alignment;
                return this;
            }

            public Builder prefixLength(int prefixLength) {
                this.prefixLength = prefixLength;
                return this;
            }

            public ParagraphStyle build() {
                return new ParagraphStyle(bullet, level, centered, alignment, prefixLength);
            }
        }
    }
}