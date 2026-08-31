package com.pptxgenerator.pipeline.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptxgenerator.common.ai.AiCallExecutor;
import com.pptxgenerator.common.ai.SystemPromptLibrary;
import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.Point;
import com.pptxgenerator.model.SlideDimensions;
import com.pptxgenerator.model.StructuralElements;
import com.pptxgenerator.model.TemplateAnalysis;
import com.pptxgenerator.model.Theme;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.SemanticType;
import com.pptxgenerator.model.enums.ZoneType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.pptx4j.pml.CTPlaceholder;
import org.pptx4j.pml.Shape;
import org.pptx4j.pml.SldLayout;
import org.pptx4j.pml.STPlaceholderType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    private final AiCallExecutor aiCallExecutor;
    private final ThemeExtractor themeExtractor;
    private final StructuralElementsDetector structuralDetector;
    private final BackgroundDetector backgroundDetector;
    private final ContentCapacityCalculator capacityCalculator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TemplateAnalyzer(AiCallExecutor aiCallExecutor,
                            ThemeExtractor themeExtractor,
                            StructuralElementsDetector structuralDetector,
                            BackgroundDetector backgroundDetector,
                            ContentCapacityCalculator capacityCalculator) {
        this.aiCallExecutor = aiCallExecutor;
        this.themeExtractor = themeExtractor;
        this.structuralDetector = structuralDetector;
        this.backgroundDetector = backgroundDetector;
        this.capacityCalculator = capacityCalculator;
    }

    /**
     * Main entry point: full analysis of the template.
     */
    public TemplateAnalysis analyze(PresentationMLPackage pptx) throws Docx4JException {
        log.info("Démarrage de l'analyse du template (port step2_layout.py)");
        long startTime = System.currentTimeMillis();

        SlideDimensions dimensions = extractSlideDimensions(pptx);
        Theme theme = themeExtractor.extract(pptx);
        List<LayoutAnalysis> layouts = analyzeLayouts(pptx, dimensions);

        layouts = enrichZoneDescriptions(layouts, dimensions);
        layouts = enrichLayoutsAndClassify(layouts, dimensions);

        StructuralElements structuralElements = structuralDetector.detect(pptx);

        TemplateAnalysis analysis = TemplateAnalysis.builder()
            .slideDimensions(dimensions)
            .theme(theme)
            .layouts(layouts)
            .structuralElements(structuralElements)
            .build();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Analyse terminée en {}ms, {} layouts détectés", duration, layouts.size());
        return analysis;
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
    private List<LayoutAnalysis> analyzeLayouts(PresentationMLPackage pptx, SlideDimensions dimensions)
            throws Docx4JException {
        List<LayoutAnalysis> layouts = new ArrayList<>();

        List<SlideLayoutPart> layoutParts = pptx.getParts().getParts().values().stream()
            .filter(SlideLayoutPart.class::isInstance)
            .map(SlideLayoutPart.class::cast)
            .toList();

        for (int i = 0; i < layoutParts.size(); i++) {
            SlideLayoutPart layoutPart = layoutParts.get(i);
            SldLayout layout = layoutPart.getContents();
            String layoutName = layout.getCSld() != null ? layout.getCSld().getName() : "layout_" + i;

            List<Zone> zones = identifyZones(layoutPart, dimensions);
            zones = backgroundDetector.detect(zones);
            var capacity = capacityCalculator.calculate(zones);

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

    private List<Zone> identifyZones(SlideLayoutPart layoutPart, SlideDimensions dimensions) throws Docx4JException {
        List<Zone> zones = new ArrayList<>();
        int zoneId = 0;
        long totalSurface = dimensions.getWidth() * dimensions.getHeight();

        SldLayout layout = layoutPart.getContents();
        if (layout.getCSld() == null || layout.getCSld().getSpTree() == null) {
            return zones;
        }

        // z_index = position in the collection (like python-pptx enumerate(layout.placeholders))
        int zIndex = 0;
        for (Object shapeObj : layout.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame()) {
            if (!(shapeObj instanceof Shape shape)) {
                zIndex++;
                continue;
            }
            if (shape.getNvSpPr() == null || shape.getNvSpPr().getNvPr() == null) {
                zIndex++;
                continue;
            }
            CTPlaceholder placeholder = shape.getNvSpPr().getNvPr().getPh();
            if (placeholder == null) {
                zIndex++;
                continue;
            }
            if (!hasExplicitGeometry(shape)) {
                zIndex++;
                continue;
            }

            ZoneType zoneType = getZoneType(placeholder, shape, dimensions);

            long x = shape.getSpPr().getXfrm().getOff().getX();
            long y = shape.getSpPr().getXfrm().getOff().getY();
            long width = shape.getSpPr().getXfrm().getExt().getCx();
            long height = shape.getSpPr().getXfrm().getExt().getCy();

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
                .zIndex(zIndex)
                .position(position)
                .idx(safeIdx(placeholder))
                .maxCharacters(computeMaxCharacters(zoneType, surfacePercentage))
                .build();

            zones.add(zone);
            zIndex++;
        }

        return zones;
    }

    private ZoneType getZoneType(CTPlaceholder placeholder, Shape shape, SlideDimensions dimensions) {
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
                case "body", "obj" -> classifyBySize(shape, dimensions);
                default -> classifyBySize(shape, dimensions);
            };
        }
        return classifyBySize(shape, dimensions);
    }

    private ZoneType classifyBySize(Shape shape, SlideDimensions dimensions) {
        long height = shape.getSpPr().getXfrm().getExt().getCy();
        long width = shape.getSpPr().getXfrm().getExt().getCx();

        double estimatedLines = height / 400000.0;
        double surfacePercentage = (width * (double) height) / (dimensions.getWidth() * (double) dimensions.getHeight()) * 100;
        double widthPercentage = (width / (double) dimensions.getWidth()) * 100;

        if (estimatedLines >= 1.5) {
            if (surfacePercentage >= 5) {
                return widthPercentage >= 15 ? ZoneType.LINE : ZoneType.WORD;
            }
            return ZoneType.BODY;
        } else {
            return widthPercentage >= 15 ? ZoneType.LINE : ZoneType.WORD;
        }
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

    private int computeMaxCharacters(ZoneType type, double surfacePercentage) {
        return switch (type) {
            case TITLE, CENTER_TITLE, SUBTITLE -> 60;
            case LINE -> 70;
            case WORD -> 3;
            case BODY -> Math.max(80, Math.min(800, (int) (surfacePercentage * 10)));
            case HEADER, FOOTER, SLIDE_NUMBER, DATE -> 40;
            default -> 0;
        };
    }

    private Long safeIdx(CTPlaceholder placeholder) {
        try {
            return placeholder.getIdx();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasExplicitGeometry(Shape shape) {
        return shape.getSpPr() != null
            && shape.getSpPr().getXfrm() != null
            && shape.getSpPr().getXfrm().getOff() != null
            && shape.getSpPr().getXfrm().getExt() != null;
    }

    // ------------------------------------------------------------------
    // Zone description enrichment (AI)
    // ------------------------------------------------------------------
    private List<LayoutAnalysis> enrichZoneDescriptions(List<LayoutAnalysis> layouts, SlideDimensions dimensions) {
        log.info("Enrichissement des descriptions de zones via IA...");

        String systemPrompt = SystemPromptLibrary.expertIntro("analyse de layouts PowerPoint")
            + "\n\n" + ZONE_SYSTEM_PROMPT.formatted(dimensions.getWidth(), dimensions.getHeight())
            + "\n\n" + SystemPromptLibrary.JSON_ONLY_DIRECTIVE;
        String userPrompt = buildZoneUserPrompt(layouts, dimensions);

        try {
            Map<String, Object> enrichedData = aiCallExecutor.call(null, systemPrompt, userPrompt, null, Map.class);

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

    private String buildZoneUserPrompt(List<LayoutAnalysis> layouts, SlideDimensions dimensions) {
        try {
            List<Map<String, Object>> context = new ArrayList<>();
            for (LayoutAnalysis layout : layouts) {
                List<Map<String, Object>> zones = new ArrayList<>();
                for (Zone zone : layout.getZones()) {
                    Map<String, Object> z = new LinkedHashMap<>();
                    z.put("zone_id", zone.getZoneId());
                    z.put("zone_type", zone.getZoneType().getValue());
                    z.put("surface_percentage", zone.getSurfacePercentage());
                    z.put("position", zone.getPosition());
                    z.put("width", zone.getWidth());
                    z.put("height", zone.getHeight());
                    z.put("z_index", zone.getZIndex());
                    z.put("top_left_x", zone.getPolygon().get(0).getX());
                    z.put("top_left_y", zone.getPolygon().get(0).getY());
                    if (zone.getZoneType() == ZoneType.WORD || zone.getZoneType() == ZoneType.LINE) {
                        double widthPercentage = (zone.getWidth() / (double) dimensions.getWidth()) * 100;
                        z.put("width_percentage", Math.round(widthPercentage * 10.0) / 10.0);
                    }
                    zones.add(z);
                }
                Map<String, Object> layoutCtx = new LinkedHashMap<>();
                layoutCtx.put("layout_id", layout.getLayoutId());
                layoutCtx.put("semantic_type", layout.getSemanticType().name());
                layoutCtx.put("layout_description", layout.getDescription());
                layoutCtx.put("zones", zones);
                context.add(layoutCtx);
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
            return "Layouts avec zones à analyser:\n" + json
                + "\n\nGénère une description enrichie pour chaque zone de chaque layout.";
        } catch (Exception e) {
            log.error("Erreur sérialisation JSON: {}", e.getMessage());
            return "Layouts avec zones à analyser: []";
        }
    }

    // ------------------------------------------------------------------
    // Layout classification and enrichment (AI)
    // ------------------------------------------------------------------
    private List<LayoutAnalysis> enrichLayoutsAndClassify(List<LayoutAnalysis> layouts, SlideDimensions dimensions) {
        log.info("Classification des layouts et enrichissement des descriptions via IA...");

        String systemPrompt = SystemPromptLibrary.expertIntro("analyse de layouts PowerPoint")
            + "\n\n" + LAYOUT_SYSTEM_PROMPT.formatted(dimensions.getWidth(), dimensions.getHeight())
            + "\n\n" + SystemPromptLibrary.JSON_ONLY_DIRECTIVE;
        String userPrompt = buildLayoutUserPrompt(layouts);

        try {
            Map<String, Object> enrichedData = aiCallExecutor.call(null, systemPrompt, userPrompt, null, Map.class);

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

    private String buildLayoutUserPrompt(List<LayoutAnalysis> layouts) {
        try {
            List<Map<String, Object>> context = new ArrayList<>();
            for (LayoutAnalysis layout : layouts) {
                List<Map<String, Object>> zonesWithDescriptions = new ArrayList<>();
                for (Zone zone : layout.getZones()) {
                    Map<String, Object> zoneInfo = new LinkedHashMap<>();
                    zoneInfo.put("zone_id", zone.getZoneId());
                    zoneInfo.put("zone_type", zone.getZoneType().getValue());
                    zoneInfo.put("surface_percentage", zone.getSurfacePercentage());
                    zoneInfo.put("position", zone.getPosition());
                    zoneInfo.put("zone_description", zone.getZoneDescription() != null
                        ? zone.getZoneDescription() : "Zone de type " + zone.getZoneType().getValue());
                    zonesWithDescriptions.add(zoneInfo);
                }
                Map<String, Object> layoutContext = new LinkedHashMap<>();
                layoutContext.put("layout_id", layout.getLayoutId());
                layoutContext.put("original_name", layout.getOriginalName());
                layoutContext.put("zones", zonesWithDescriptions);
                context.add(layoutContext);
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
            return "Layouts à analyser:\n" + json
                + "\n\nGénère la description ET ensuite classifie le semantic_type pour chaque layout.";
        } catch (Exception e) {
            log.error("Erreur sérialisation JSON: {}", e.getMessage());
            return "Layouts à analyser: []";
        }
    }

    // ------------------------------------------------------------------
    // AI prompts
    // ------------------------------------------------------------------
    private static final String ZONE_SYSTEM_PROMPT = """
        Tu reçois les dimensions d'une slide (%d x %d EMU) et la liste des layouts avec leurs zones.

        Pour chaque zone de chaque layout, génère une description précise incluant :
        - Le rôle de la zone dans le layout (ex: "Zone de titre principale", "Sous-titre aligné sous le titre", "Ligne décorative")
        - Le contexte du layout (semantic_type: TITLE_SLIDE, SECTION_HEADER, CONTENT, etc.)
        - La position de la zone géographiquement dans la slide (haut, bas, gauche, droite, centrée)
        - Les contraintes de contenu basées sur le zone_type:
          * "body": Texte multilingue, paragraphes, listes à puces
          * "title", "center_title", "subtitle": Titres courts (max 8 mots)
          * "line": Texte court sur une seule ligne (max 10 mots). IMPORTANT: Mentionner si c'est un sous-titre (aligné sous titre) ou une ligne décorative (non alignée)
          * "word": Texte très court (1-3 caractères, chiffre, lettre, ou expression courte). Inclure width_percentage et usage probable (numéro de section "01", label "Contexte")

        - Les recommandations de densité basées sur la surface (ex: "Grande surface (60%%), peut accueillir du contenu dense")

        La description doit être concise (1-2 phrases) et aider à générer le bon contenu pour cette zone.

        Réponds UNIQUEMENT avec un JSON valide au format:
        {"enriched_zones": [
          {"layout_id": "layout_0", "zone_id": 0, "zone_description": "Description de la zone..."},
          ...
        ]}
        """;

    private static final String LAYOUT_SYSTEM_PROMPT = """
        Tu reçois les dimensions d'une slide (%d x %d EMU) et la liste des layouts avec leurs zones DÉJÀ ENRICHIES, ainsi que le nom original du layout.

        Pour chaque layout, génère dans l'ORDRE:
        1. layout_description: Description enrichie du layout (2-3 phrases)
        2. semantic_type: Classification selon les règles strictes ci-dessous

        Pour la description enrichie de layout, inclure:
        - Les proportions relatives des zones (%% de la surface totale déjà calculée)
        - La position des zones géographiquement dans la slide lorsque l'on compare leur top left avec la hauteur et la largeur (haut, bas, gauche, droite, centrée)
        - Les cas d'usage idéaux pour ce layout
        - Les points de différenciation avec les autres layouts

        Types sémantiques valides:
        TITLE_SLIDE: Slides d'ouverture
        * Zones title, center_title ou line positionnées au CENTRE du slide (position générale centrée verticalement)
        * Zones body minimales en %% de surface ou inexistantes
        * Peut avoir des zones picture
        * Souvent avec une ou plusieurs line alignées sous ou une autre line ou un titre
        * INDICE FORT: Si original_name contient "Titre" avec plusieurs zones centrées verticalement (middle) → probablement TITLE_SLIDE

        SECTION_HEADER: Slides de transition entre sections
        * Zones title,center_title ou line positionnées au CENTRE du slide (position générale centrée verticalement)
        * Peut contenir une zone body ou word à gauche ou au centre pour la numérotation des sections.
        * Zones body ou word minimales en %% de surface ou inexistantes
        * Peut avoir des zones picture
        * Ne contient pas de lines décorative. En général un word pour la numérotation et une line ou un title pour le nom de section
        * INDICE FORT: Si original_name contient "Titre" ET zones centrées verticalement (middle) ET pas de sous-titre aligné → probablement SECTION_HEADER

        OUTLINE: Sommaire ou table des matières
        * Plusieurs zones line/word positionnées les unes sous les autres.
        * Zones word pour numérotation

        CONTENT: Contenu textuel standard
        * Au moins une zone body >= 20%% de surface
        * Zones title/line en HAUT du slide (position: top, top-left, top-right, centrée horizontalement)
        * PAS de zones picture/chart/table

        TWO_COLUMN: Comparaison côte à côte
        * 2 grandes zones body (positions left et right)
        * Chaque zone body >= 15%% de surface

        CONTENT_WITH_MEDIA: Contenu avec média
        * Au moins une zone body >= 15%% de surface
        * Au moins une zone picture/chart/table
        * Zones title/line en HAUT du slide

        CUSTOM: Layouts non exploitables programmatiquement
        * Zones title/line en HAUT du slide (position: top)
        * Somme des zones body < 5%% OU pas de zone body
        * Grand espace vide au centre

        BLANK: Slide vide (aucune zone)

        La description doit être concise (max 2-3 phrases) et aider à choisir le bon layout.

        Réponds UNIQUEMENT avec un JSON valide au format:
        {"enriched_layouts": [
          {"layout_id": "layout_0", "layout_description": "Description enrichie ici...", "semantic_type": "TITLE_SLIDE"},
          ...
        ]}
        """;
}
