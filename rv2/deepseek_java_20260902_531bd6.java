package com.pptxgenerator.pipeline.renderer;

import com.pptxgenerator.model.Point;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.ZoneKeys;
import com.pptxgenerator.model.enums.ZoneType;
import com.pptxgenerator.pipeline.renderer.model.RenderWarning;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.pptx4j.pml.CommonSlideData;
import org.pptx4j.pml.GroupShape;
import org.pptx4j.pml.Shape;

import java.util.*;

/**
 * Mappe les zones logiques vers les placeholders concrets et injecte le contenu.
 * 
 * <p>Deux responsabilités principales :
 * <ol>
 *   <li>Mapper chaque zone à son placeholder cloné (via idx, type, position)</li>
 *   <li>Injecter le texte dans les placeholders mappés</li>
 * </ol>
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PlaceholderMapper {

    /** Tolérance de position (EMU) pour le matching. */
    private static final long POSITION_TOLERANCE = 100_000L;

    /** Tolérance de dimension (EMU) pour le matching. */
    private static final long DIMENSION_TOLERANCE = 100_000L;

    /** Mapping ZoneType → types OOXML acceptés. */
    private static final Map<ZoneType, Set<String>> ACCEPTED_TYPES = Map.ofEntries(
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

    private final OoxmlHelper ooxml;

    // ========================================================================
    // MAPPING
    // ========================================================================

    /**
     * Construit le mapping zone → placeholder pour une diapositive.
     *
     * @param slidePart   la diapositive (déjà peuplée)
     * @param layoutZones zones déclarées pour le layout de la diapositive
     * @return une map clé par {@code ZONE_TYPE_ZONE_ID}
     */
    public Map<String, Shape> mapPlaceholders(SlidePart slidePart, List<Zone> layoutZones) {
        Map<String, Shape> mapping = new HashMap<>();
        if (layoutZones == null || layoutZones.isEmpty()) {
            return mapping;
        }

        GroupShape spTree = slidePart.getContents().getCSld().getSpTree();
        if (spTree == null) {
            log.warn("Slide '{}' has no shape tree; cannot map zones.", slidePart.getPartName());
            return mapping;
        }

        List<Shape> placeholders = ooxml.extractPlaceholders(spTree);
        log.debug("Mapping {} zone(s) against {} placeholder(s).", layoutZones.size(), placeholders.size());

        for (Zone zone : layoutZones) {
            String zoneKey = ZoneKeys.key(zone);
            Shape placeholder = findPlaceholder(placeholders, zone);
            if (placeholder != null) {
                mapping.put(zoneKey, placeholder);
                log.debug("Mapped zone '{}' to placeholder id={}.",
                        zoneKey, ooxml.getId(placeholder));
            } else {
                log.warn("No placeholder matched zone '{}'.", zoneKey);
            }
        }
        return mapping;
    }

    /**
     * Trouve le placeholder correspondant à une zone.
     * Stratégie : idx → type → position → dimensions.
     */
    private Shape findPlaceholder(List<Shape> placeholders, Zone zone) {
        // 1. Match exact par idx OOXML (le plus fiable)
        if (zone.getIdx() != null) {
            Optional<Shape> byIdx = placeholders.stream()
                    .filter(ph -> ooxml.getId(ph) == zone.getIdx())
                    .findFirst();
            if (byIdx.isPresent()) {
                return byIdx.get();
            }
        }

        // 2. Match par type
        Set<String> accepted = ACCEPTED_TYPES.getOrDefault(
                zone.getZoneType(),
                Set.of("body", "ftr", "obj")
        );
        List<Shape> typeMatches = placeholders.stream()
                .filter(ph -> isTypeCompatible(ph, accepted))
                .toList();

        if (typeMatches.isEmpty()) {
            return null;
        }
        if (typeMatches.size() == 1) {
            return typeMatches.get(0);
        }

        // 3. Tie-break par position
        List<Shape> positioned = filterByPosition(typeMatches, zone);
        if (positioned.size() == 1) {
            return positioned.get(0);
        }

        // 4. Tie-break par dimensions (fallback)
        List<Shape> dimensioned = filterByDimension(
                positioned.isEmpty() ? typeMatches : positioned,
                zone
        );
        return dimensioned.isEmpty() ? positioned.get(0) : dimensioned.get(0);
    }

    /**
     * Vérifie si le type du placeholder est compatible avec les types acceptés.
     */
    private boolean isTypeCompatible(Shape placeholder, Set<String> accepted) {
        String type = ooxml.getType(placeholder);
        if (type == null) {
            return accepted.contains("body") || accepted.contains("ftr");
        }
        return accepted.contains(type);
    }

    /**
     * Filtre les candidats par position (tolérance POSITION_TOLERANCE).
     */
    private List<Shape> filterByPosition(List<Shape> candidates, Zone zone) {
        long zoneX = getMinX(zone);
        long zoneY = getMinY(zone);

        return candidates.stream()
                .filter(ooxml::hasGeometry)
                .filter(ph -> Math.abs(ooxml.getX(ph) - zoneX) < POSITION_TOLERANCE
                        && Math.abs(ooxml.getY(ph) - zoneY) < POSITION_TOLERANCE)
                .toList();
    }

    /**
     * Filtre les candidats par dimensions (tolérance DIMENSION_TOLERANCE).
     */
    private List<Shape> filterByDimension(List<Shape> candidates, Zone zone) {
        long zoneW = zone.getWidth() != null ? zone.getWidth() : 0L;
        long zoneH = zone.getHeight() != null ? zone.getHeight() : 0L;

        return candidates.stream()
                .filter(ooxml::hasGeometry)
                .filter(ph -> Math.abs(ooxml.getWidth(ph) - zoneW) < DIMENSION_TOLERANCE
                        && Math.abs(ooxml.getHeight(ph) - zoneH) < DIMENSION_TOLERANCE)
                .toList();
    }

    /**
     * Récupère la coordonnée X minimale du polygone de la zone.
     */
    private long getMinX(Zone zone) {
        if (zone.getPolygon() == null || zone.getPolygon().isEmpty()) {
            return 0L;
        }
        return zone.getPolygon().stream()
                .mapToLong(Point::getX)
                .min()
                .orElse(0L);
    }

    /**
     * Récupère la coordonnée Y minimale du polygone de la zone.
     */
    private long getMinY(Zone zone) {
        if (zone.getPolygon() == null || zone.getPolygon().isEmpty()) {
            return 0L;
        }
        return zone.getPolygon().stream()
                .mapToLong(Point::getY)
                .min()
                .orElse(0L);
    }

    // ========================================================================
    // INJECTION
    // ========================================================================

    /**
     * Injecte le contenu dans la diapositive.
     *
     * @param slidePart   la diapositive à remplir
     * @param zoneText    map zoneKey → texte (voir SlideContentZoneResolver)
     * @param layoutPart  le layout de la diapositive (source des placeholders clonés)
     * @param layoutZones zones déclarées pour le layout
     * @return warnings collectés pendant l'injection
     */
    public List<RenderWarning> inject(SlidePart slidePart,
                                      Map<String, String> zoneText,
                                      SlideLayoutPart layoutPart,
                                      List<Zone> layoutZones) {
        List<RenderWarning> warnings = new ArrayList<>();

        if (layoutZones == null || layoutZones.isEmpty()) {
            return warnings;
        }
        if (zoneText == null || zoneText.isEmpty()) {
            return warnings;
        }

        try {
            // 1. Clone des placeholders du layout
            int clonedCount = clonePlaceholders(slidePart, layoutPart);
            log.debug("Cloned {} placeholder(s) from layout into slide.", clonedCount);

            // 2. Mapping zones → placeholders
            Map<String, Shape> zoneToShape = mapPlaceholders(slidePart, layoutZones);

            // 3. Injection du texte
            int injectedCount = 0;
            for (Zone zone : layoutZones) {
                String zoneKey = ZoneKeys.key(zone);
                Shape shape = zoneToShape.get(zoneKey);

                if (shape == null || shape.getTxBody() == null) {
                    continue;
                }

                String text = zoneText.get(zoneKey);
                if (text == null || text.isBlank()) {
                    continue;
                }

                ooxml.clearText(shape);
                injectZoneText(shape, text, zone.getZoneType());
                injectedCount++;
            }

            log.debug("Injected content into {} zone(s).", injectedCount);

        } catch (Exception e) {
            log.error("Placeholder injection failed for slide '{}'.", slidePart.getPartName(), e);
            warnings.add(RenderWarning.builder()
                    .code("INJECTION_FAILED")
                    .message(e.getMessage())
                    .build());
        }

        return warnings;
    }

    /**
     * Clone chaque placeholder du layout dans l'arbre de la diapositive.
     */
    private int clonePlaceholders(SlidePart slidePart, SlideLayoutPart layoutPart) throws Docx4JException {
        GroupShape slideTree = getOrCreateShapeTree(slidePart);
        GroupShape layoutTree = layoutPart.getContents().getCSld().getSpTree();

        if (layoutTree == null) {
            return 0;
        }

        // ID de départ : 1000 + nombre de formes existantes pour éviter les collisions
        long nextId = 1000L + slideTree.getSpOrGrpSpOrGraphicFrame().size();
        int clonedCount = 0;

        for (Shape layoutShape : ooxml.extractPlaceholders(layoutTree)) {
            Shape cloned = ooxml.cloneShape(layoutShape, nextId++);
            slideTree.getSpOrGrpSpOrGraphicFrame().add(cloned);
            clonedCount++;
        }

        return clonedCount;
    }

    /**
     * Récupère ou crée l'arbre de formes de la diapositive.
     */
    private GroupShape getOrCreateShapeTree(SlidePart slidePart) {
        if (slidePart.getContents().getCSld() == null) {
            slidePart.getContents().setCSld(new CommonSlideData());
        }
        if (slidePart.getContents().getCSld().getSpTree() == null) {
            slidePart.getContents().getCSld().setSpTree(new GroupShape());
        }
        return slidePart.getContents().getCSld().getSpTree();
    }

    /**
     * Injecte le texte selon le type de zone.
     */
    private void injectZoneText(Shape shape, String text, ZoneType zoneType) {
        switch (zoneType) {
            case TITLE, SUBTITLE, LINE, WORD -> 
                ooxml.addParagraph(shape, text, OoxmlHelper.ParagraphStyle.simple());

            case CENTER_TITLE -> 
                ooxml.addParagraph(shape, text, OoxmlHelper.ParagraphStyle.centered());

            case BODY -> 
                ooxml.injectBodyText(shape, text);

            case PICTURE, CHART, TABLE, BACKGROUND -> 
                log.debug("Zone type {} is not text-filled by this renderer.", zoneType);

            default -> 
                ooxml.addParagraph(shape, text, OoxmlHelper.ParagraphStyle.simple());
        }
    }
}