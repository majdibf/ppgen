package com.pptxgenerator.analyzer.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.SlideDimensions;
import com.pptxgenerator.model.Zone;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class LayoutClassificationPromptBuilder {

    private final ObjectMapper objectMapper;

    /**
     * PROMPT IDENTIQUE AU PYTHON
     */
    public String buildSystemPrompt(SlideDimensions dimensions) {
        return """
            Tu es un expert en analyse de layouts PowerPoint.

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
            """.formatted(dimensions.getWidth(), dimensions.getHeight());
    }

    /**
     * Construit le prompt utilisateur (contexte des layouts + zones déjà enrichies, sérialisé en JSON).
     */
    public String buildUserPrompt(List<LayoutAnalysis> layouts, SlideDimensions dimensions) {
        try {
            List<Map<String, Object>> context = buildContext(layouts, dimensions);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
            return "Layouts à analyser:\n" + json + "\n\nGénère la description ET ensuite classifie le semantic_type pour chaque layout.";
        } catch (Exception e) {
            log.error("Erreur sérialisation JSON: {}", e.getMessage());
            return "Layouts à analyser: []";
        }
    }

    private List<Map<String, Object>> buildContext(List<LayoutAnalysis> layouts, SlideDimensions dimensions) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (LayoutAnalysis layout : layouts) {
            List<Map<String, Object>> zonesWithDescriptions = new ArrayList<>();

            for (Zone zone : layout.getZones()) {
                Map<String, Object> zoneInfo = new LinkedHashMap<>();
                zoneInfo.put("zone_id", zone.getZoneId());
                zoneInfo.put("zone_type", zone.getZoneType().getValue());
                zoneInfo.put("surface_percentage", zone.getSurfacePercentage());
                zoneInfo.put("position", zone.getPosition());
                zoneInfo.put("zone_description", zone.getZoneDescription() != null ?
                    zone.getZoneDescription() : "Zone de type " + zone.getZoneType().getValue());

                zonesWithDescriptions.add(zoneInfo);
            }

            Map<String, Object> layoutContext = new LinkedHashMap<>();
            layoutContext.put("layout_id", layout.getLayoutId());
            layoutContext.put("original_name", layout.getOriginalName());
            layoutContext.put("zones", zonesWithDescriptions);

            result.add(layoutContext);
        }

        return result;
    }
}
