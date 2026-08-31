package com.pptxgenerator.pipeline.renderer;

import com.pptxgenerator.model.Point;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.ZoneKeys;
import com.pptxgenerator.model.enums.ZoneType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.pptx4j.pml.CTPlaceholder;
import org.pptx4j.pml.GroupShape;
import org.pptx4j.pml.Shape;

import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Links each planned {@link Zone} of a slide to the concrete placeholder {@link Shape} cloned
 * from its layout.
 *
 * <p>Matching is attempted in decreasing order of confidence:
 * <ol>
 *   <li><b>OOXML idx</b> — when the zone carries the placeholder index from the template, it is
 *       matched exactly against the cloned placeholder's idx (this is the most reliable key);</li>
 *   <li><b>Type</b> — the placeholder OOXML type must be compatible with the zone type;</li>
 *   <li><b>Position</b> — when several placeholders share the same type, the closest one (by the
 *       zone's bounding box) is selected. Geometry is only available when the zone declares a
 *       polygon; otherwise the first type-compatible placeholder is used as a fallback.</li>
 * </ol>
 */
@Slf4j
@ApplicationScoped
public class PlaceholderMapper {

    /** Max distance (EMU) for a placeholder position to be considered a match for a zone. */
    private static final long POSITION_TOLERANCE = 100_000L;
    /** Max difference (EMU) for placeholder dimensions to be considered a match for a zone. */
    private static final long DIMENSION_TOLERANCE = 100_000L;

    /** Zone type -> OOXML placeholder type values that may host it. */
    @SuppressWarnings("unchecked")
    private static final Map<ZoneType, Set<String>> ACCEPTED_PLACEHOLDER_TYPES = Map.ofEntries(
            Map.entry(ZoneType.TITLE, Set.of("title")),
            Map.entry(ZoneType.CENTER_TITLE, Set.of("ctrTitle", "title")),
            Map.entry(ZoneType.SUBTITLE, Set.of("subTitle", "body")),
            Map.entry(ZoneType.BODY, Set.of("body", "ftr", "obj")),
            Map.entry(ZoneType.LINE, Set.of("body", "ftr", "obj")),
            Map.entry(ZoneType.WORD, Set.of("body", "ftr", "obj")),
            Map.entry(ZoneType.PICTURE, Set.of("pic")),
            Map.entry(ZoneType.CHART, Set.of("chart", "body")),
            Map.entry(ZoneType.TABLE, Set.of("tbl", "body")),
            Map.entry(ZoneType.HEADER, Set.of("hdr", "body")),
            Map.entry(ZoneType.FOOTER, Set.of("ftr", "body")),
            Map.entry(ZoneType.SLIDE_NUMBER, Set.of("sldNum", "body")),
            Map.entry(ZoneType.DATE, Set.of("dt", "body")),
            Map.entry(ZoneType.UNKNOWN, Set.of("body", "ftr", "obj"))
    );

    /**
     * Builds the zone-key -> placeholder shape mapping for a slide.
     *
     * @param slidePart   the (already populated) slide
     * @param layoutZones zones declared for the slide's layout
     * @return a map keyed by {@code ZONE_TYPE_ZONE_ID}
     */
    public Map<String, Shape> mapPlaceholders(SlidePart slidePart, List<Zone> layoutZones) throws Docx4JException {
        Map<String, Shape> mapping = new HashMap<>();
        if (layoutZones == null || layoutZones.isEmpty()) {
            return mapping;
        }

        GroupShape spTree = slidePart.getContents().getCSld().getSpTree();
        if (spTree == null) {
            log.warn("Slide '{}' has no shape tree yet; cannot map zones.", slidePart.getPartName());
            return mapping;
        }
        List<Shape> placeholders = extractPlaceholders(spTree);
        log.debug("Mapping {} zone(s) against {} placeholder(s).", layoutZones.size(), placeholders.size());

        for (Zone zone : layoutZones) {
            String zoneKey = zoneKey(zone);
            Shape placeholder = findPlaceholderForZone(placeholders, zone);
            if (placeholder != null) {
                mapping.put(zoneKey, placeholder);
                log.debug("Mapped zone '{}' to placeholder id={}.",
                        zoneKey, placeholder.getNvSpPr().getCNvPr().getId());
            } else {
                log.warn("No placeholder matched zone '{}'.", zoneKey);
            }
        }
        return mapping;
    }

    private Shape findPlaceholderForZone(List<Shape> placeholders, Zone zone) {
        // 1. Exact OOXML idx match (most reliable).
        if (zone.getIdx() != null) {
            long wantedIdx = zone.getIdx();
            Optional<Shape> byIdx = placeholders.stream()
                    .filter(ph -> idxOf(ph) == wantedIdx)
                    .findFirst();
            if (byIdx.isPresent()) {
                return byIdx.get();
            }
        }

        // 2. Type match.
        Set<String> acceptedTypes = ACCEPTED_PLACEHOLDER_TYPES.getOrDefault(zone.getZoneType(),
                Set.of("body", "ftr", "obj"));
        List<Shape> typeMatches = placeholders.stream()
                .filter(ph -> isTypeCompatible(ph, acceptedTypes))
                .toList();
        if (typeMatches.isEmpty()) {
            return null;
        }
        if (typeMatches.size() == 1) {
            return typeMatches.get(0);
        }

        // 3. Position / dimension tie-break.
        List<Shape> positioned = matchByPosition(typeMatches, zone);
        if (positioned.size() == 1) {
            return positioned.get(0);
        }
        List<Shape> dimensioned = matchByDimension(positioned.isEmpty() ? typeMatches : positioned, zone);
        return (dimensioned.isEmpty() ? positioned : dimensioned).get(0);
    }

    private boolean isTypeCompatible(Shape placeholder, Set<String> acceptedTypes) {
        CTPlaceholder ph = placeholderOf(placeholder);
        if (ph == null || ph.getType() == null) {
            return acceptedTypes.contains("body") || acceptedTypes.contains("ftr");
        }
        return acceptedTypes.contains(ph.getType().value());
    }

    private List<Shape> matchByPosition(List<Shape> candidates, Zone zone) {
        long zoneX = zoneX(zone);
        long zoneY = zoneY(zone);
        return candidates.stream()
                .filter(this::hasExplicitGeometry)
                .filter(ph -> Math.abs(x(ph) - zoneX) < POSITION_TOLERANCE
                        && Math.abs(y(ph) - zoneY) < POSITION_TOLERANCE)
                .toList();
    }

    private List<Shape> matchByDimension(List<Shape> candidates, Zone zone) {
        long zoneW = zone.getWidth() != null ? zone.getWidth() : 0L;
        long zoneH = zone.getHeight() != null ? zone.getHeight() : 0L;
        return candidates.stream()
                .filter(this::hasExplicitGeometry)
                .filter(ph -> Math.abs(width(ph) - zoneW) < DIMENSION_TOLERANCE
                        && Math.abs(height(ph) - zoneH) < DIMENSION_TOLERANCE)
                .toList();
    }

    private boolean hasExplicitGeometry(Shape shape) {
        return shape.getSpPr() != null
                && shape.getSpPr().getXfrm() != null
                && shape.getSpPr().getXfrm().getOff() != null
                && shape.getSpPr().getXfrm().getExt() != null;
    }

    private long idxOf(Shape shape) {
        CTPlaceholder ph = placeholderOf(shape);
        return ph == null ? -1L : ph.getIdx();
    }

    private long x(Shape shape) {
        return shape.getSpPr().getXfrm().getOff().getX();
    }

    private long y(Shape shape) {
        return shape.getSpPr().getXfrm().getOff().getY();
    }

    private long width(Shape shape) {
        return shape.getSpPr().getXfrm().getExt().getCx();
    }

    private long height(Shape shape) {
        return shape.getSpPr().getXfrm().getExt().getCy();
    }

    /** Left coordinate of the zone's bounding box (from its polygon), or 0 when absent. */
    private long zoneX(Zone zone) {
        return boundingMin(zone, Point::getX);
    }

    private long zoneY(Zone zone) {
        return boundingMin(zone, Point::getY);
    }

    private long boundingMin(Zone zone, java.util.function.ToLongFunction<Point> accessor) {
        if (zone.getPolygon() == null || zone.getPolygon().isEmpty()) {
            return 0L;
        }
        return zone.getPolygon().stream().mapToLong(accessor).min().orElse(0L);
    }

    /** Builds the stable key identifying a zone within a slide. */
    static String zoneKey(Zone zone) {
        return ZoneKeys.key(zone);
    }

    /**
     * Extracts every shape that carries a placeholder definition ({@code <p:ph>}) from a shape tree.
     */
    static List<Shape> extractPlaceholders(GroupShape spTree) {
        List<Shape> placeholders = new ArrayList<>();
        if (spTree == null) {
            return placeholders;
        }
        for (Object obj : spTree.getSpOrGrpSpOrGraphicFrame()) {
            if (obj instanceof Shape shape
                    && shape.getNvSpPr() != null
                    && shape.getNvSpPr().getNvPr() != null
                    && shape.getNvSpPr().getNvPr().getPh() != null) {
                placeholders.add(shape);
            }
        }
        return placeholders;
    }

    /**
     * @return the placeholder definition of a shape, or {@code null} when the shape is not a placeholder.
     */
    static CTPlaceholder placeholderOf(Shape shape) {
        if (shape.getNvSpPr() == null || shape.getNvSpPr().getNvPr() == null) {
            return null;
        }
        return shape.getNvSpPr().getNvPr().getPh();
    }
}
