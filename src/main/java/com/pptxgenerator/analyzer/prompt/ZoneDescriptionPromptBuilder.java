package com.pptxgenerator.analyzer.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.SlideDimensions;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ZoneType;
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
public class ZoneDescriptionPromptBuilder {

    private final ObjectMapper objectMapper;

    /**
     * PROMPT IDENTIQUE AU PYTHON
     */
    public String buildSystemPrompt(SlideDimensions dimensions) {
        return """
            Tu es un expert en analyse de layouts PowerPoint.

            Tu reçois les dimensions d'une slide (%d x %d EMU) et la liste des layouts avec leurs zones.

            Pour chaque zone de chaque layout, génère une description précise incluant:
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
            """.formatted(dimensions.getWidth(), dimensions.getHeight());
    }

    /**
     * Construit le prompt utilisateur (contexte des layouts sérialisé en JSON).
     */
    public String buildUserPrompt(List<LayoutAnalysis> layouts, SlideDimensions dimensions) {
        try {
            List<Map<String, Object>> context = buildContext(layouts, dimensions);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
            return "Layouts avec zones à analyser:\n" + json +
                   "\n\nGénère une description enrichie pour chaque zone de chaque layout.";
        } catch (Exception e) {
            log.error("Erreur sérialisation JSON: {}", e.getMessage());
            return "Layouts avec zones à analyser: []";
        }
    }

    private List<Map<String, Object>> buildContext(List<LayoutAnalysis> layouts, SlideDimensions dimensions) {
        List<Map<String, Object>> result = new ArrayList<>();

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

                // Ajouter width_percentage pour word et line (comme le Python)
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
            result.add(layoutCtx);
        }

        return result;
    }
}
