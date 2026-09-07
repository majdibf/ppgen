package com.pptxgenerator.pipeline.renderer;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Mappe les zones logiques vers les placeholders concrets et injecte le contenu.
 *
 * <p>Deux responsabilités principales :
 * <ol>
 *   <li>Mapper chaque zone à son placeholder cloné (jointure par idx + type,
 *       indépendante de l'ordre des listes)</li>
 *   <li>Injecter le texte dans les placeholders mappés</li>
 * </ol>
 *
 * <p>La jointure repose sur l'identité OOXML du placeholder ({@code idx}, avec une
 * compatibilité de famille de type), capturée côté analyse par {@code TemplateAnalyzer}.
 * Aucun ordre de liste n'est requis : changer le côté analyse ne peut donc plus
 * décaler le rendu. Les zones sans correspondance produisent un warning explicite.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PlaceholderMapper {

    private final OoxmlHelper ooxml;

    // ========================================================================
    // MAPPING
    // ========================================================================

    /**
     * Construit le mapping zone → placeholder pour une diapositive par jointure sur
     * l'identité du placeholder (idx + type compatibles), indépendamment de l'ordre
     * des listes.
     *
     * <p>Règles de correspondance :
     * <ul>
     *   <li>une zone portant un {@code idx} est jointe au placeholder de même `idx`</li>
     *   <li>les zones sans `idx` (titre famille, footer, numérique...) sont jointes par type</li>
     *   <li>la famille de type doit toujours être compatible</li>
     * </ul>
     *
     * @param slidePart   la diapositive (déjà peuplée par {@link #clonePlaceholders})
     * @param layoutZones zones déclarées pour le layout de la diapositive
     * @return une map clé par {@code ZONE_TYPE_ZONE_ID}
     */
    public Map<String, Shape> mapPlaceholders(SlidePart slidePart, List<Zone> layoutZones) throws Docx4JException {
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
        log.debug("Mapping {} zone(s) against {} placeholder(s) by (idx, type).",
                layoutZones.size(), placeholders.size());

        Set<Shape> used = new HashSet<>();
        for (Zone zone : layoutZones) {
            Optional<Shape> match = matchPlaceholder(zone, placeholders, used);
            if (match.isEmpty()) {
                log.warn("No placeholder matches zone '{}' (idx={}, type={}) on slide '{}'.",
                        ZoneKeys.key(zone), zone.getIdx(), zone.getZoneType(), slidePart.getPartName());
                continue;
            }
            Shape placeholder = match.get();
            used.add(placeholder);
            mapping.put(ZoneKeys.key(zone), placeholder);
            log.debug("Mapped zone '{}' to placeholder type={} idx={}.",
                    ZoneKeys.key(zone), ooxml.getType(placeholder), ooxml.getId(placeholder));
        }
        return mapping;
    }

    /**
     * Joint une zone au placeholder correspondant : idx d'abord, puis type.
     */
    private Optional<Shape> matchPlaceholder(Zone zone, List<Shape> placeholders, Set<Shape> used) {
        return placeholders.stream()
                .filter(shape -> !used.contains(shape))
                .filter(shape -> typeCompatible(zone.getZoneType(), ooxml.getType(shape)))
                .filter(shape -> zone.getIdx() == null || ooxml.getId(shape) == zone.getIdx())
                .findFirst();
    }

    /**
     * Compatibilité entre le type sémantique d'une zone et le type OOXML du placeholder.
     */
    private boolean typeCompatible(ZoneType zoneType, String placeholderType) {
        if (placeholderType == null) {
            return zoneType == ZoneType.UNKNOWN;
        }
        return switch (zoneType) {
            case TITLE, CENTER_TITLE -> placeholderType.equals("title") || placeholderType.equals("ctrTitle");
            case SUBTITLE -> placeholderType.equals("subTitle") || placeholderType.equals("body");
            case BODY, LINE, WORD, BACKGROUND -> placeholderType.equals("body") || placeholderType.equals("obj");
            case HEADER -> placeholderType.equals("hdr");
            case FOOTER -> placeholderType.equals("ftr");
            case SLIDE_NUMBER -> placeholderType.equals("sldNum");
            case DATE -> placeholderType.equals("dt");
            case PICTURE -> placeholderType.equals("pic");
            case CHART -> placeholderType.equals("chart");
            case TABLE -> placeholderType.equals("tbl");
            case UNKNOWN -> true;
            default -> false;
        };
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
    private GroupShape getOrCreateShapeTree(SlidePart slidePart) throws Docx4JException {
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
