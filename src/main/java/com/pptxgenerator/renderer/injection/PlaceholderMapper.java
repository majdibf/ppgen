package com.pptxgenerator.renderer.injection;

import com.pptxgenerator.model.Zone;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.pptx4j.pml.CTPlaceholder;
import org.pptx4j.pml.GroupShape;
import org.pptx4j.pml.Shape;
import org.pptx4j.pml.Sld;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mappe les zones nommées du contenu vers les placeholders physiques de la slide.
 * Conformément à la section 5.3 de la spec.
 */
@Slf4j
@ApplicationScoped
public class PlaceholderMapper {

    /**
     * Indexe les placeholders d'une slide par zone sémantique.
     */
    public Map<String, Shape> mapPlaceholders(SlidePart slidePart, List<Zone> layoutZones) throws Docx4JException {
        Map<String, Shape> mapping = new HashMap<>();

        Sld slide = slidePart.getContents();
        if (slide.getCSld() == null || slide.getCSld().getSpTree() == null) {
            return mapping;
        }

        GroupShape spTree = slide.getCSld().getSpTree();
        List<Shape> allPlaceholders = extractPlaceholders(spTree);

        // Mapper selon les règles de la spec (section 5.3)
        for (Zone zone : layoutZones) {
            String semanticName = zone.getSemanticName();
            if (semanticName == null) continue;

            Shape placeholder = findMatchingPlaceholder(semanticName, allPlaceholders, layoutZones);
            if (placeholder != null) {
                mapping.put(semanticName, placeholder);
            }
        }

        return mapping;
    }

    /**
     * Extrait tous les placeholders d'un spTree.
     */
    private List<Shape> extractPlaceholders(GroupShape spTree) {
        List<Shape> placeholders = new ArrayList<>();

        for (Object obj : spTree.getSpOrGrpSpOrGraphicFrame()) {
            if (!(obj instanceof Shape shape)) continue;

            if (shape.getNvSpPr() != null &&
                shape.getNvSpPr().getNvPr() != null &&
                shape.getNvSpPr().getNvPr().getPh() != null) {
                placeholders.add(shape);
            }
        }

        return placeholders;
    }

    /**
     * Trouve le placeholder correspondant à une zone sémantique.
     */
    private Shape findMatchingPlaceholder(String semanticName, List<Shape> placeholders, List<Zone> layoutZones) {
        return switch (semanticName) {
            case "title" -> findByType(placeholders, "title", "ctrTitle");
            case "subtitle" -> findByType(placeholders, "subTitle");
            case "body" -> findLargestBody(placeholders);
            case "left_column" -> findBodyByPosition(placeholders, 0, layoutZones);
            case "right_column" -> findBodyByPosition(placeholders, 1, layoutZones);
            case "box_1" -> findBodyByPosition(placeholders, 0, layoutZones);
            case "box_2" -> findBodyByPosition(placeholders, 1, layoutZones);
            case "box_3" -> findBodyByPosition(placeholders, 2, layoutZones);
            case "media_placeholder" -> findByType(placeholders, "pic", "obj");
            default -> null;
        };
    }

    /**
     * Trouve un placeholder par type.
     */
    private Shape findByType(List<Shape> placeholders, String... types) {
        for (Shape shape : placeholders) {
            CTPlaceholder ph = shape.getNvSpPr().getNvPr().getPh();
            if (ph.getType() != null) {
                String typeValue = ph.getType().value();
                for (String type : types) {
                    if (typeValue.equals(type)) {
                        return shape;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Trouve le plus grand placeholder BODY.
     */
    private Shape findLargestBody(List<Shape> placeholders) {
        return placeholders.stream()
            .filter(shape -> {
                CTPlaceholder ph = shape.getNvSpPr().getNvPr().getPh();
                return ph.getType() == null || "body".equals(ph.getType().value());
            })
            .filter(this::hasExplicitGeometry)
            .max(Comparator.comparingLong(shape -> {
                long width = shape.getSpPr().getXfrm().getExt().getCx();
                long height = shape.getSpPr().getXfrm().getExt().getCy();
                return width * height;
            }))
            .orElse(null);
    }

    /**
     * Trouve un placeholder BODY par position (trié par X croissant).
     */
    private Shape findBodyByPosition(List<Shape> placeholders, int index, List<Zone> layoutZones) {
        List<Shape> bodies = placeholders.stream()
            .filter(shape -> {
                CTPlaceholder ph = shape.getNvSpPr().getNvPr().getPh();
                return ph.getType() == null || "body".equals(ph.getType().value());
            })
            .filter(this::hasExplicitGeometry)
            .sorted(Comparator.comparingLong(shape -> shape.getSpPr().getXfrm().getOff().getX()))
            .toList();

        return index < bodies.size() ? bodies.get(index) : null;
    }

    /**
     * Certains placeholders (souvent sldNum/ftr/dt) n'ont pas de xfrm propre :
     * leur position/taille est héritée du slide master, pas du layout/slide lui-même.
     */
    private boolean hasExplicitGeometry(Shape shape) {
        return shape.getSpPr() != null
            && shape.getSpPr().getXfrm() != null
            && shape.getSpPr().getXfrm().getOff() != null
            && shape.getSpPr().getXfrm().getExt() != null;
    }
}