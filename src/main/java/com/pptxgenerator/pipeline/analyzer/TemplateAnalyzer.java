package com.pptxgenerator.pipeline.analyzer;

import com.pptxgenerator.common.ai.AiCallExecutor;
import com.pptxgenerator.pipeline.common.ooxml.OoxmlShapes;
import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.Point;
import com.pptxgenerator.model.SlideDimensions;
import com.pptxgenerator.model.StructuralElements;
import com.pptxgenerator.model.TemplateAnalysis;
import com.pptxgenerator.model.Theme;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ContentCapacity;
import com.pptxgenerator.model.enums.SemanticType;
import com.pptxgenerator.model.enums.ZoneType;
import com.pptxgenerator.model.Theme;
import com.pptxgenerator.model.FontStyle;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.dml.CTTextListStyle;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlideMasterPart;
import org.pptx4j.pml.CTPlaceholder;
import org.pptx4j.pml.Shape;
import org.pptx4j.pml.SldLayout;
import org.pptx4j.pml.SldMaster;
import org.pptx4j.pml.STPlaceholderType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Java port of {@code other_codes/step2_layout.py} (TemplateAnalyzer class).
 *
 * Analyzes a PowerPoint template and produces a {@link TemplateAnalysis}:
 * dimensions, theme, layouts (zones + semantic type), structural elements.
 * Zone description enrichment and semantic layout classification are done by
 * AI (with graceful fallback if the call fails).
 */
@Slf4j
@ApplicationScoped
public class TemplateAnalyzer {

    private static final int DEFAULT_FONT_SIZE = 14;

    private final AiCallExecutor aiCallExecutor;
    private final ThemeExtractor themeExtractor;
    private final StructuralElementsDetector structuralDetector;
    private final ZoneCapacityCalculator zoneCapacityCalculator;
    private final AnalyzerPromptBuilder promptBuilder;
    private final InheritedGeometryResolver geometryResolver;

    public TemplateAnalyzer(AiCallExecutor aiCallExecutor,
                            ThemeExtractor themeExtractor,
                            StructuralElementsDetector structuralDetector,
                            ZoneCapacityCalculator zoneCapacityCalculator,
                            AnalyzerPromptBuilder promptBuilder,
                            InheritedGeometryResolver geometryResolver) {
        this.aiCallExecutor = aiCallExecutor;
        this.themeExtractor = themeExtractor;
        this.structuralDetector = structuralDetector;
        this.zoneCapacityCalculator = zoneCapacityCalculator;
        this.promptBuilder = promptBuilder;
        this.geometryResolver = geometryResolver;
    }

    /**
     * Main entry point: full analysis of the template.
     *
     * @param modelId user-requested model id (nullable: falls back to the provider default)
     */
    public TemplateAnalysis analyze(PresentationMLPackage pptx, String modelId) throws Docx4JException {
        log.info("Démarrage de l'analyse du template (port step2_layout.py)");
        long startTime = System.currentTimeMillis();

        SlideDimensions dimensions = extractSlideDimensions(pptx);
        Theme theme = themeExtractor.extract(pptx);
        List<LayoutAnalysis> layouts = analyzeLayouts(pptx, dimensions, theme);

        layouts = enrichZoneDescriptions(layouts, dimensions, modelId);
        layouts = enrichLayoutsAndClassify(layouts, dimensions, modelId);

        StructuralElements structuralElements = structuralDetector.detect(pptx);

        TemplateAnalysis analysis = TemplateAnalysis.builder()
            .slideDimensions(dimensions)
            .theme(theme)
            .layouts(layouts)
            .structuralElements(structuralElements)
            .build();

        validateAnalysis(analysis);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Analyse terminée en {}ms, {} layouts détectés", duration, layouts.size());
        return analysis;
    }

    /**
     * Validates the minimal coherence of a template analysis (former
     * TemplateAnalysisValidator, inlined because this analyzer is its only consumer).
     */
    private void validateAnalysis(TemplateAnalysis analysis) {
        if (analysis.getSlideDimensions() == null
            || analysis.getSlideDimensions().getWidth() == null
            || analysis.getSlideDimensions().getHeight() == null
            || analysis.getSlideDimensions().getWidth() <= 0
            || analysis.getSlideDimensions().getHeight() <= 0) {
            throw new IllegalStateException("Dimensions de slide invalides");
        }
        if (analysis.getLayouts() == null || analysis.getLayouts().isEmpty()) {
            throw new IllegalStateException("Aucun layout détecté dans le template");
        }
        for (LayoutAnalysis layout : analysis.getLayouts()) {
            if (layout.getZones() == null) {
                throw new IllegalStateException("Zones nulles pour le layout " + layout.getLayoutId());
            }
        }
        if (analysis.getTheme() == null) {
            log.warn("Aucun thème détecté pour le template");
        }
        if (analysis.getStructuralElements() == null) {
            log.warn("Aucun élément structurel détecté pour le template");
        }
    }

    // ------------------------------------------------------------------
    // Slide dimensions
    // ------------------------------------------------------------------
    private SlideDimensions extractSlideDimensions(PresentationMLPackage pptx) throws Docx4JException {
        long width = pptx.getMainPresentationPart().getContents().getSldSz().getCx();
        long height = pptx.getMainPresentationPart().getContents().getSldSz().getCy();
        return SlideDimensions.builder()
            .width(width)
            .height(height)
            .unit("EMU")
            .build();
    }

    // ------------------------------------------------------------------
    // Layout analysis and zone identification
    // ------------------------------------------------------------------
    private List<LayoutAnalysis> analyzeLayouts(PresentationMLPackage pptx, SlideDimensions dimensions, Theme theme)
            throws Docx4JException {
        List<LayoutAnalysis> layouts = new ArrayList<>();

        List<SlideLayoutPart> layoutParts = pptx.getParts().getParts().values().stream()
            .filter(SlideLayoutPart.class::isInstance)
            .map(SlideLayoutPart.class::cast)
            .toList();

        for (int i = 0; i < layoutParts.size(); i++) {
            SlideLayoutPart layoutPart = layoutParts.get(i);
            SldLayout layout = layoutPart.getContents();
            String csldName = OoxmlShapes.nameOf(layout.getCSld());
            String layoutName = csldName != null ? csldName : "layout_" + i;

            List<Zone> zones = identifyZones(layoutPart, dimensions, theme);
            zones = reclassifyBackgroundZones(zones);
            var capacity = calculateCapacity(zones);

            List<String> zoneTypes = zones.stream().map(z -> z.getZoneType().getValue()).toList();
            String descriptionHint = "To be classified by AI based on zones: "
                + String.join(", ", zoneTypes.stream().limit(3).toList()) + "...";

            layouts.add(LayoutAnalysis.builder()
                .layoutId("layout_" + i)
                .originalName(layoutName)
                .semanticType(SemanticType.PENDING)
                .description(descriptionHint)
                .contentCapacity(capacity)
                .zones(zones)
                .build());
        }

        return layouts;
    }

    /**
     * Computes the content capacity of a layout (HIGH/MEDIUM/LOW) from the
     * cumulative surface of its BODY zones. Contract field of the template
     * analysis (spec: content_capacity).
     */
    private ContentCapacity calculateCapacity(List<Zone> zones) {
        double totalBodySurface = zones.stream()
            .filter(z -> z.getZoneType() == ZoneType.BODY)
            .mapToDouble(Zone::getSurfacePercentage)
            .sum();

        if (totalBodySurface >= 40) {
            return ContentCapacity.HIGH;
        } else if (totalBodySurface >= 20) {
            return ContentCapacity.MEDIUM;
        }
        return ContentCapacity.LOW;
    }

    /**
     * Reclassifies text zones (BODY/LINE/WORD) as BACKGROUND when a low z-index
     * zone is covered at more than 60% by the other zones. Former
     * BackgroundDetector, inlined because this analyzer is its only consumer.
     */
    private List<Zone> reclassifyBackgroundZones(List<Zone> zones) {
        for (Zone zone : zones) {
            if (!isTextCapableZone(zone.getZoneType()) || zone.getZIndex() > 1) {
                continue;
            }

            if (isBackground(zone, zones)) {
                zone.setZoneType(ZoneType.BACKGROUND);
                log.debug("Zone {} reclassée comme background", zone.getZoneId());
            }
        }
        return zones;
    }

    private boolean isTextCapableZone(ZoneType zoneType) {
        return zoneType == ZoneType.BODY || zoneType == ZoneType.LINE || zoneType == ZoneType.WORD;
    }

    private boolean isBackground(Zone zone, List<Zone> allZones) {
        long zoneArea = zone.getWidth() * zone.getHeight();
        if (zoneArea == 0) {
            return false;
        }

        long totalOverlapArea = 0;
        for (Zone other : allZones) {
            if (other.getZoneId().equals(zone.getZoneId())) {
                continue;
            }
            totalOverlapArea += calculateOverlapArea(zone, other);
        }

        double coveragePercentage = (totalOverlapArea / (double) zoneArea) * 100;
        return coveragePercentage > 60.0;
    }

    /**
     * Calculates the intersection area between two zones (in EMU²).
     */
    private long calculateOverlapArea(Zone zone1, Zone zone2) {
        Point p1 = zone1.getPolygon().get(0); // top-left
        Point p2 = zone1.getPolygon().get(2); // bottom-right
        Point p3 = zone2.getPolygon().get(0);
        Point p4 = zone2.getPolygon().get(2);

        long xOverlap = Math.max(0, Math.min(p2.getX(), p4.getX()) - Math.max(p1.getX(), p3.getX()));
        long yOverlap = Math.max(0, Math.min(p2.getY(), p4.getY()) - Math.max(p1.getY(), p3.getY()));

        return xOverlap * yOverlap;
    }

    private List<Zone> identifyZones(SlideLayoutPart layoutPart, SlideDimensions dimensions, Theme theme)
            throws Docx4JException {
        List<Zone> zones = new ArrayList<>();
        int zoneId = 0;
        long totalSurface = dimensions.getWidth() * dimensions.getHeight();

        SldLayout layout = layoutPart.getContents();

        // Aligned with python-pptx enumerate(layout.placeholders): only placeholders are
        // enumerated (z_index counts them), and geometry is resolved through inheritance
        // from the master when the layout placeholder has no explicit xfrm.
        // Shapes whose geometry cannot be resolved at all are skipped from the analysis;
        // this must stay consistent with the renderer (see OoxmlShapes, the shared read
        // side used by PlaceholderMapper, which matches placeholders by (idx, type)).
        int zIndex = 0;
        for (Shape shape : OoxmlShapes.placeholderShapesIn(layout.getCSld() == null
                ? null : layout.getCSld().getSpTree())) {
            CTPlaceholder placeholder = OoxmlShapes.placeholderOf(shape);
            InheritedGeometryResolver.Geometry resolved =
                    geometryResolver.resolve(layoutPart, shape).orElse(null);
            if (resolved == null) {
                log.debug("Placeholder '{}' of layout {} has no resolvable geometry; skipped.",
                        placeholder, layoutPart.getPartName());
                continue;
            }
            int placeholderIndex = zIndex++;

            ZoneType zoneType = getZoneType(placeholder, resolved, dimensions);

            long x = resolved.x();
            long y = resolved.y();
            long width = resolved.width();
            long height = resolved.height();

            List<Point> polygon = List.of(
                new Point(x, y),
                new Point(x + width, y),
                new Point(x + width, y + height),
                new Point(x, y + height)
            );

            double surfacePercentage = (width * (double) height / totalSurface) * 100;
            String position = describePosition(x, y, width, height, dimensions);

            Zone zone = Zone.builder()
                .zoneId(zoneId++)
                .zoneType(zoneType)
                .width(width)
                .height(height)
                .polygon(polygon)
                .surfacePercentage(Math.round(surfacePercentage * 10.0) / 10.0)
                .zIndex(placeholderIndex)
                .position(position)
                .idx(OoxmlShapes.idxOf(shape))
                .build();

            // Physical capacity: real EMU geometry + the EFFECTIVE font size of the
            // placeholder (layout lstStyle → master txStyles → theme body), minus
            // padding and a safety margin. Unsupported types (CENTER_TITLE, SUBTITLE,
            // PICTURE, ...) get null, matching the client contract.
            int fontSize = fontSizeForZone(layoutPart, shape, zoneType, theme);
            Integer maxCharacters = zoneCapacityCalculator.calculateMaxCharacters(zone, fontSize);
            zone.setMaxCharacters(maxCharacters);
            zone.setZoneDescription(zoneCapacityCalculator.enrichDescription(
                null, maxCharacters, zoneType));

            zones.add(zone);
        }

        return zones;
    }

    private ZoneType getZoneType(CTPlaceholder placeholder,
                                 InheritedGeometryResolver.Geometry geometry,
                                 SlideDimensions dimensions) {
        STPlaceholderType type = placeholder.getType();
        if (type != null) {
            return switch (type.value()) {
                case "pic" -> ZoneType.PICTURE;
                case "chart" -> ZoneType.CHART;
                case "tbl" -> ZoneType.TABLE;
                case "hdr" -> ZoneType.HEADER;
                case "ftr" -> ZoneType.FOOTER;
                case "sldNum" -> ZoneType.SLIDE_NUMBER;
                case "dt" -> ZoneType.DATE;
                case "title" -> ZoneType.TITLE;
                case "ctrTitle" -> ZoneType.CENTER_TITLE;
                case "subTitle" -> ZoneType.SUBTITLE;
                case "body", "obj" -> classifyBySize(geometry, dimensions);
                default -> classifyBySize(geometry, dimensions);
            };
        }
        return classifyBySize(geometry, dimensions);
    }

    /**
     * Classifies a body-family placeholder by its geometry.
     *
     * <p>Historical note: the POC (other_codes/analyzer.py) returned LINE/WORD for
     * large multi-line zones, contradicting its own comment ("check if surface is
     * large enough for body") and making BODY unreachable on templates whose text
     * placeholders are large (e.g. "Titre et contenu" layouts). This was corrected:
     * a large multi-line zone is a BODY; only small or single-line zones fall back
     * to LINE/WORD by width.
     */
    private ZoneType classifyBySize(InheritedGeometryResolver.Geometry geometry, SlideDimensions dimensions) {
        long height = geometry.height();
        long width = geometry.width();

        double estimatedLines = height / 400000.0;
        double surfacePercentage = (width * (double) height) / (dimensions.getWidth() * (double) dimensions.getHeight()) * 100;
        double widthPercentage = (width / (double) dimensions.getWidth()) * 100;

        if (estimatedLines >= 1.5) {
            // multi-line: large enough to hold paragraphs -> body
            if (surfacePercentage >= 5) {
                return ZoneType.BODY;
            }
            // multi-line but narrow: thin column -> line/word by width
            return widthPercentage >= 15 ? ZoneType.LINE : ZoneType.WORD;
        }
        // single line: wide -> line, narrow -> word
        return widthPercentage >= 15 ? ZoneType.LINE : ZoneType.WORD;
    }

    private String describePosition(long x, long y, long width, long height, SlideDimensions dimensions) {
        long centerX = x + width / 2;
        long centerY = y + height / 2;
        long slideWidth = dimensions.getWidth();
        long slideHeight = dimensions.getHeight();

        String vertical = centerY < slideHeight * 0.33 ? "top" : centerY > slideHeight * 0.67 ? "bottom" : "middle";
        String horizontal = centerX < slideWidth * 0.33 ? "left" : centerX > slideWidth * 0.67 ? "right" : "center";
        return vertical + ": " + horizontal;
    }

        /**
     * Resolves the EFFECTIVE font size (in pt) of a placeholder, in inheritance order:
     * the layout placeholder's own lstStyle (lvl1), then the master text styles
     * (title/body/other), then the theme body font, then a 14pt default. The theme
     * size alone is unreliable: masters/layouts routinely override it (e.g. an
     * "Ordre du jour" body declared at 24pt while the theme says 14pt).
     */
    private int fontSizeForZone(SlideLayoutPart layoutPart, Shape shape, ZoneType zoneType, Theme theme) {
        Integer sz = shapeListStyleSize(shape);
        if (sz == null) {
            sz = masterStyleSize(layoutPart, zoneType);
        }
        if (sz == null) {
            sz = themeBodySize(theme);
        }
        return sz != null ? Math.max(1, sz) : DEFAULT_FONT_SIZE;
    }

    private Integer shapeListStyleSize(Shape shape) {
        try {
            if (shape.getTxBody() == null
                || shape.getTxBody().getLstStyle() == null
                || shape.getTxBody().getLstStyle().getLvl1PPr() == null
                || shape.getTxBody().getLstStyle().getLvl1PPr().getDefRPr() == null) {
                return null;
            }
            Integer sz = shape.getTxBody().getLstStyle().getLvl1PPr().getDefRPr().getSz();
            return sz == null ? null : sz / 100;
        } catch (Exception e) {
            return null;
        }
    }

    private Integer masterStyleSize(SlideLayoutPart layoutPart, ZoneType zoneType) {
        try {
            SlideMasterPart masterPart = layoutPart.getSlideMasterPart();
            if (masterPart == null) {
                return null;
            }
            SldMaster master = masterPart.getContents();
            if (master == null || master.getTxStyles() == null) {
                return null;
            }
            CTTextListStyle style = switch (zoneType) {
                case TITLE -> master.getTxStyles().getTitleStyle();
                case BODY -> master.getTxStyles().getBodyStyle();
                default -> master.getTxStyles().getOtherStyle();
            };
            if (style == null || style.getLvl1PPr() == null || style.getLvl1PPr().getDefRPr() == null) {
                return null;
            }
            Integer sz = style.getLvl1PPr().getDefRPr().getSz();
            return sz == null ? null : sz / 100;
        } catch (Exception e) {
            return null;
        }
    }

    private Integer themeBodySize(Theme theme) {
        if (theme == null || theme.getFonts() == null) {
            return null;
        }
        FontStyle bodyFont = theme.getFonts().get("body");
        return bodyFont != null && bodyFont.getSizePt() != null ? bodyFont.getSizePt() : null;
    }

    // ------------------------------------------------------------------
    // Zone description enrichment (AI)
    // ------------------------------------------------------------------
    private List<LayoutAnalysis> enrichZoneDescriptions(List<LayoutAnalysis> layouts, SlideDimensions dimensions, String modelId) {
        log.info("Enrichissement des descriptions de zones via IA...");

        String systemPrompt = promptBuilder.buildZoneSystemPrompt(dimensions.getWidth(), dimensions.getHeight());
        String userPrompt = promptBuilder.buildZoneUserPrompt(layouts, dimensions);

        try {
            Map<String, Object> enrichedData = aiCallExecutor.call(modelId, systemPrompt, userPrompt, null, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> enrichedZones = (List<Map<String, Object>>) enrichedData.get("enriched_zones");
            if (enrichedZones == null || enrichedZones.isEmpty()) {
                return applyDefaultZoneDescriptions(layouts);
            }

            Map<String, String> zoneDescriptions = new java.util.HashMap<>();
            for (Map<String, Object> item : enrichedZones) {
                String layoutId = (String) item.get("layout_id");
                Integer zoneId = (Integer) item.get("zone_id");
                String description = (String) item.get("zone_description");
                if (layoutId != null && zoneId != null && description != null) {
                    zoneDescriptions.put(layoutId + "_" + zoneId, description);
                }
            }

            for (LayoutAnalysis layout : layouts) {
                for (Zone zone : layout.getZones()) {
                    String key = layout.getLayoutId() + "_" + zone.getZoneId();
                    if (zoneDescriptions.containsKey(key)) {
                        zone.setZoneDescription(zoneDescriptions.get(key));
                    }
                }
            }
            return layouts;
        } catch (Exception e) {
            log.error("Erreur enrichissement zones: {}", e.getMessage());
            return applyDefaultZoneDescriptions(layouts);
        }
    }

    private List<LayoutAnalysis> applyDefaultZoneDescriptions(List<LayoutAnalysis> layouts) {
        for (LayoutAnalysis layout : layouts) {
            for (Zone zone : layout.getZones()) {
                if (zone.getZoneDescription() == null) {
                    zone.setZoneDescription("Zone de type " + zone.getZoneType().getValue()
                        + " (" + zone.getSurfacePercentage() + "% de la surface)");
                }
            }
        }
        return layouts;
    }

    // ------------------------------------------------------------------
    // Layout classification and enrichment (AI)
    // ------------------------------------------------------------------
    private List<LayoutAnalysis> enrichLayoutsAndClassify(List<LayoutAnalysis> layouts, SlideDimensions dimensions, String modelId) {
        log.info("Classification des layouts et enrichissement des descriptions via IA...");

        String systemPrompt = promptBuilder.buildLayoutSystemPrompt(dimensions.getWidth(), dimensions.getHeight());
        String userPrompt = promptBuilder.buildLayoutUserPrompt(layouts);

        try {
            Map<String, Object> enrichedData = aiCallExecutor.call(modelId, systemPrompt, userPrompt, null, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> enrichedLayouts = (List<Map<String, Object>>) enrichedData.get("enriched_layouts");
            if (enrichedLayouts == null || enrichedLayouts.isEmpty()) {
                log.warn("Réponse IA vide ou mal formée, conservation des valeurs par défaut");
                return layouts;
            }

            Map<String, Map<String, String>> enrichedLayoutsMap = new java.util.HashMap<>();
            for (Map<String, Object> item : enrichedLayouts) {
                String layoutId = (String) item.get("layout_id");
                String description = (String) item.get("layout_description");
                String semanticType = (String) item.get("semantic_type");
                if (layoutId != null && description != null && semanticType != null) {
                    enrichedLayoutsMap.put(layoutId, Map.of("description", description, "semantic_type", semanticType));
                }
            }

            for (LayoutAnalysis layout : layouts) {
                Map<String, String> enriched = enrichedLayoutsMap.get(layout.getLayoutId());
                if (enriched != null) {
                    layout.setDescription(enriched.get("description"));
                    layout.setSemanticType(parseSemanticType(enriched.get("semantic_type")));
                }
            }
            return layouts;
        } catch (Exception e) {
            log.error("Erreur enrichissement layouts: {}", e.getMessage());
            log.warn("Descriptions de base conservées");
            return layouts;
        }
    }

    private SemanticType parseSemanticType(String value) {
        try {
            return SemanticType.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("semantic_type inconnu retourné par l'IA: '{}', repli sur CUSTOM", value);
            return SemanticType.CUSTOM;
        }
    }
}
