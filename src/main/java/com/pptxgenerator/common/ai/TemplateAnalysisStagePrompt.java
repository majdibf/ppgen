package com.pptxgenerator.common.ai;

/**
 * Prompt constants for the template analysis stage (M1): the system prompt bodies for zone
 * enrichment and layout classification, plus the user prompt scaffolding. The
 * {@code AnalyzerPromptBuilder} owns the actual building logic (JSON serialization, dimension
 * formatting).
 *
 * <p>{@code %d x %d EMU} placeholders are formatted with the slide dimensions; {@code %%} is an
 * escaped percent sign for {@link String#formatted(Object...)}.
 */
public enum TemplateAnalysisStagePrompt {
    ZONE_ENRICHMENT,
    LAYOUT_ENRICHMENT;

    /** System prompt body for zone descriptive enrichment. */
    public static final String ZONE_SYSTEM_BODY = """
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

    /** System prompt body for layout description enrichment and semantic classification. */
    public static final String LAYOUT_SYSTEM_BODY = """
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
