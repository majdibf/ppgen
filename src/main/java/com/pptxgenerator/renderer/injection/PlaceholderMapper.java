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

    public Map<String, Shape> mapPlaceholders(SlidePart slidePart, List<Zone> layoutZones) throws Docx4JException {
        Map<String, Shape> mapping = new HashMap<>();

        Sld slide = slidePart.getContents();
        if (slide.getCSld() == null || slide.getCSld().getSpTree() == null) {
            return mapping;
        }

        GroupShape spTree = slide.getCSld().getSpTree();
        List<Shape> allPlaceholders = extractPlaceholders(spTree);

        // ÉTAPE 1 : Mapping simple pour title/subtitle
        mapSimplePlaceholders(allPlaceholders, layoutZones, mapping);

        // ÉTAPE 2 : Mapping intelligent pour les bodies (avec zIndex comme tie-breaker)
        mapBodyPlaceholders(allPlaceholders, layoutZones, mapping);

        // ÉTAPE 3 : Mapping pour les médias
        mapMediaPlaceholders(allPlaceholders, layoutZones, mapping);

        log.info("Mapping final : {}", mapping.keySet());
        return mapping;
    }

    private void mapSimplePlaceholders(List<Shape> placeholders, List<Zone> zones, Map<String, Shape> mapping) {
        for (Zone zone : zones) {
            String semanticName = zone.getSemanticName();
            if (semanticName == null) continue;

            Shape placeholder = switch (semanticName) {
                case "title", "center_title" -> findByType(placeholders, "title", "ctrTitle");
                case "subtitle" -> findByType(placeholders, "subTitle");
                default -> null;
            };

            if (placeholder != null) {
                mapping.put(semanticName, placeholder);
                log.debug("✓ {} → placeholder trouvé", semanticName);
            }
        }
    }

    /**
     * 🔑 MAPPING INTELLIGENT avec zIndex comme critère de tri secondaire
     */
    private void mapBodyPlaceholders(List<Shape> placeholders, List<Zone> zones, Map<String, Shape> mapping) {
        // 1. Trier les placeholders body par position X
        List<Shape> bodyPlaceholders = placeholders.stream()
                .filter(shape -> {
                    CTPlaceholder ph = shape.getNvSpPr().getNvPr().getPh();
                    return ph.getType() == null || "body".equals(ph.getType().value());
                })
                .filter(this::hasExplicitGeometry)
                .sorted(Comparator.comparingLong(shape -> shape.getSpPr().getXfrm().getOff().getX()))
                .toList();

        // 2. Trier les zones body par position X, puis par zIndex (tie-breaker)
        List<Zone> bodyZones = zones.stream()
                .filter(zone -> {
                    String name = zone.getSemanticName();
                    return name != null && (
                            "body".equals(name) ||
                                    "left_column".equals(name) ||
                                    "right_column".equals(name) ||
                                    name.startsWith("box_")
                    );
                })
                .filter(zone -> zone.getPolygon() != null && !zone.getPolygon().isEmpty())
                .sorted(Comparator
                        .comparingLong((Zone zone) -> zone.getPolygon().get(0).getX())
                        .thenComparingInt(zone -> zone.getZIndex() != null ? zone.getZIndex() : 0)) // ← zIndex comme tie-breaker
                .toList();

        // 3. Associer index par index
        int matchCount = Math.min(bodyPlaceholders.size(), bodyZones.size());
        for (int i = 0; i < matchCount; i++) {
            Shape placeholder = bodyPlaceholders.get(i);
            Zone zone = bodyZones.get(i);

            mapping.put(zone.getSemanticName(), placeholder);
            log.debug("✓ {} → body placeholder (X={}, zIndex={})",
                    zone.getSemanticName(),
                    placeholder.getSpPr().getXfrm().getOff().getX(),
                    zone.getZIndex());
        }

        if (bodyPlaceholders.size() != bodyZones.size()) {
            log.warn("⚠️ Ambiguïté : {} placeholders body mais {} zones body",
                    bodyPlaceholders.size(), bodyZones.size());
        }
    }

    private void mapMediaPlaceholders(List<Shape> placeholders, List<Zone> zones, Map<String, Shape> mapping) {
        for (Zone zone : zones) {
            if ("media_placeholder".equals(zone.getSemanticName())) {
                Shape placeholder = findByType(placeholders, "pic", "obj");
                if (placeholder != null) {
                    mapping.put("media_placeholder", placeholder);
                    log.debug("✓ media_placeholder → placeholder trouvé");
                }
            }
        }
    }

    private List<Shape> extractPlaceholders(GroupShape spTree) {
        List<Shape> placeholders = new ArrayList<>();
        for (Object obj : spTree.getSpOrGrpSpOrGraphicFrame()) {
            if (!(obj instanceof Shape shape)) continue;
            if (shape.getNvSpPr() != null && shape.getNvSpPr().getNvPr() != null && shape.getNvSpPr().getNvPr().getPh() != null) {
                placeholders.add(shape);
            }
        }
        return placeholders;
    }

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

    private boolean hasExplicitGeometry(Shape shape) {
        return shape.getSpPr() != null
                && shape.getSpPr().getXfrm() != null
                && shape.getSpPr().getXfrm().getOff() != null
                && shape.getSpPr().getXfrm().getExt() != null;
    }
}