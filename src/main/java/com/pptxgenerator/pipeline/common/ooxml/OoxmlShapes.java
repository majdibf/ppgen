package com.pptxgenerator.pipeline.common.ooxml;

import org.pptx4j.pml.CommonSlideData;
import org.pptx4j.pml.CTPlaceholder;
import org.pptx4j.pml.GroupShape;
import org.pptx4j.pml.Shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared OOXML read side: the shape/placeholder primitives used by BOTH the
 * analyzer (which captures each placeholder identity) and the renderer (which
 * re-finds them by (idx, type)). The analyzer↔renderer contract relies on these
 * exact accessors, so they live in one place instead of being duplicated per side.
 *
 * <p>All accessors are null-safe: a malformed shape never throws, it just yields
 * null/empty/absent. Write operations (cloning, text injection) stay in the
 * renderer-specific {@code OoxmlHelper}.
 */
public final class OoxmlShapes {

    /**
     * Explicit position/size of a shape (in EMU), as declared in its {@code <a:xfrm>}.
     */
    public record ExplicitGeometry(long x, long y, long width, long height) {
    }

    private OoxmlShapes() {
    }

    /**
     * Returns the placeholder definition ({@code <p:ph>}) of a shape, or null.
     */
    public static CTPlaceholder placeholderOf(Shape shape) {
        if (shape == null || shape.getNvSpPr() == null || shape.getNvSpPr().getNvPr() == null) {
            return null;
        }
        return shape.getNvSpPr().getNvPr().getPh();
    }

    /**
     * Whether the shape carries a placeholder definition.
     */
    public static boolean isPlaceholder(Shape shape) {
        return placeholderOf(shape) != null;
    }

    /**
     * The OOXML placeholder type value (title, body, pic...), or null.
     */
    public static String typeOf(Shape shape) {
        CTPlaceholder placeholder = placeholderOf(shape);
        return placeholder != null && placeholder.getType() != null ? placeholder.getType().value() : null;
    }

    /**
     * The OOXML placeholder idx, or null when absent. docx4j may throw while
     * marshalling optional attributes, hence the guarded access.
     */
    public static Long idxOf(Shape shape) {
        CTPlaceholder placeholder = placeholderOf(shape);
        if (placeholder == null) {
            return null;
        }
        try {
            return placeholder.getIdx();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Whether the shape carries an explicit {@code <a:xfrm>} (position + size).
     */
    public static boolean hasExplicitGeometry(Shape shape) {
        return shape != null
            && shape.getSpPr() != null
            && shape.getSpPr().getXfrm() != null
            && shape.getSpPr().getXfrm().getOff() != null
            && shape.getSpPr().getXfrm().getExt() != null;
    }

    /**
     * The shape explicit geometry, or empty when it has none.
     */
    public static Optional<ExplicitGeometry> geometryOf(Shape shape) {
        if (!hasExplicitGeometry(shape)) {
            return Optional.empty();
        }
        return Optional.of(new ExplicitGeometry(
            shape.getSpPr().getXfrm().getOff().getX(),
            shape.getSpPr().getXfrm().getOff().getY(),
            shape.getSpPr().getXfrm().getExt().getCx(),
            shape.getSpPr().getXfrm().getExt().getCy()));
    }

    /**
     * All concrete shapes ({@code <p:sp>}) of a shape tree, in document order.
     */
    public static List<Shape> shapesIn(GroupShape spTree) {
        List<Shape> shapes = new ArrayList<>();
        if (spTree == null) {
            return shapes;
        }
        for (Object obj : spTree.getSpOrGrpSpOrGraphicFrame()) {
            if (obj instanceof Shape shape) {
                shapes.add(shape);
            }
        }
        return shapes;
    }

    /**
     * The placeholder shapes ({@code <p:ph>}) of a shape tree, in document order.
     */
    public static List<Shape> placeholderShapesIn(GroupShape spTree) {
        return shapesIn(spTree).stream()
            .filter(OoxmlShapes::isPlaceholder)
            .toList();
    }

    /**
     * The name of a slide part common data, or null. Used by both the layout
     * factory (naming) and the analyzer (layout naming fallback).
     */
    public static String nameOf(CommonSlideData csld) {
        return csld != null ? csld.getName() : null;
    }
}