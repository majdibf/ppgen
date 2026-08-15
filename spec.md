Absolument ! Voici la **spécification technique complète** pour l'ensemble du projet, avec tous les détails pour chaque étape.

---

# SPÉCIFICATION TECHNIQUE COMPLÈTE V3
## Générateur de Présentations PPTX Piloté par IA

---

## 📋 TABLE DES MATIÈRES

1. [Vue d'ensemble](#1-vue-densemble)
2. [Architecture générale](#2-architecture-générale)
3. [Milestone 1 : Analyseur de template](#3-milestone-1--analyseur-de-template)
4. [Milestone 2 : Planificateur de présentation](#4-milestone-2--planificateur-de-présentation)
5. [Milestone 3 : Layout Assigner](#5-milestone-3--layout-assigner)
6. [Milestone 4 : Générateur IA de contenu](#6-milestone-4--générateur-ia-de-contenu)
7. [Milestone 5 : Renderer PPTX](#7-milestone-5--renderer-pptx)
8. [Milestone 6 : Intégration et orchestration](#8-milestone-6--intégration-et-orchestration)
9. [Exemple d'utilisation complet](#9-exemple-dutilisation-complet)
10. [Planning et livrables](#10-planning-et-livrables)

---

## 1. VUE D'ENSEMBLE

### Objectif du projet

Développer une librairie Java qui génère des présentations PowerPoint à partir de templates, avec orchestration IA pour :
- La planification de la structure
- L'assignation des layouts
- La génération de contenu (textes, tableaux, graphiques)
- Le rendu final fidèle au template

### Problème résolu

| Problème | Solution |
| :--- | :--- |
| Créer des présentations rapidement | Génération automatisée via IA |
| Maintenir l'identité visuelle | Utilisation de templates PowerPoint |
| Adapter le contenu dynamiquement | IA qui génère le contenu selon le contexte |
| Gérer des cas complexes | Création dynamique de tableaux/graphiques |

### Concepts clés

| Concept | Définition |
| :--- | :--- |
| **Layout** | Modèle de diapositive (placeholders, positions, styles) |
| **Slide réel** | Diapositive utilisée dans la présentation (contient des données) |
| **Zone** | Espace dans un layout (title, body, picture, etc.) |
| **Placeholder** | Espace réservé dans un layout (à remplir par l'IA) |
| **Slide dynamique** | Slide créée à la volée quand le template n'a pas de layout adapté |

---

## 2. ARCHITECTURE GÉNÉRALE

### Diagramme d'architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              TEMPLATE PPTX                                     │
│                    (fichier .pptx avec layouts et slides)                       │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│  [M1] ANALYSEUR DE TEMPLATE                                                    │
│  - Parcourt /ppt/slideLayouts/*.xml (layouts uniquement)                       │
│  - Extrait placeholders, positions, styles                                     │
│  - Associe slides réels → layouts                                             │
│  - Génère JSON structure                                                       │
│  → Output : TemplateStructure (JSON)                                           │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│  [M2] PLANIFICATEUR DE PRÉSENTATION                                           │
│  - Reçoit sujet, public, contraintes                                          │
│  - Appelle IA pour générer le plan                                            │
│  - Définit types de slides (cover, section, content, etc.)                    │
│  → Output : PresentationPlan (JSON)                                            │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│  [M3] LAYOUT ASSIGNER                                                          │
│  - Match plan ↔ layouts du template                                           │
│  - Utilise semantic_type (TITLE_SLIDE, SECTION_HEADER, CONTENT)               │
│  - Marque slides sans layout comme "dynamiques"                               │
│  → Output : EnrichedPlan (JSON)                                                │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│  [M4] GÉNÉRATEUR IA DE CONTENU                                               │
│  - Pour chaque slide, appelle IA avec contexte                                │
│  - Génère textes, données tableaux, données graphiques                        │
│  - Enrichit le plan avec le contenu                                           │
│  → Output : ContentMap (Map<String, SlideContent>)                            │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│  [M5] RENDERER PPTX                                                            │
│  - Si layout existe → remplit les zones avec docx4j                           │
│  - Si dynamique → crée composants à la volée                                  │
│  - Applique styles exacts                                                     │
│  → Output : presentation_finale.pptx                                           │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Stack technique

| Composant | Technologie | Version |
| :--- | :--- | :--- |
| Langage | Java | 21 LTS |
| Manipulation PPTX | docx4j | 11.5.14 |
| Sérialisation JSON | Jackson | 2.18.2 |
| HTTP Client | OkHttp | 4.12.0 |
| Logging | SLF4J + Logback | 2.0.x |
| Tests | JUnit | 5.10.x |
| Build | Maven ou Gradle | - |

### Structure des packages

```
com.presentation.generator/
├── analyzer/                 # M1 - Analyseur
│   ├── TemplateAnalyzer.java
│   ├── LayoutAnalyzer.java
│   ├── ZoneAnalyzer.java
│   └── StyleExtractor.java
├── planner/                  # M2 - Planificateur
│   ├── PresentationPlanner.java
│   ├── PlanRequest.java
│   └── AIProvider.java
├── assigner/                 # M3 - Layout Assigner
│   ├── LayoutAssigner.java
│   ├── LayoutMatcher.java
│   └── DynamicLayoutDetector.java
├── generator/                # M4 - Générateur IA
│   ├── AIContentGenerator.java
│   ├── ContentPromptBuilder.java
│   └── ContentParser.java
├── renderer/                 # M5 - Renderer
│   ├── PresentationRenderer.java
│   ├── ZoneRenderer.java
│   ├── ChartRenderer.java
│   └── TableRenderer.java
├── model/                    # Modèles de données
│   ├── TemplateStructure.java
│   ├── PresentationPlan.java
│   ├── EnrichedPlan.java
│   ├── SlideContent.java
│   └── ...
├── config/                   # Configuration
│   └── GeneratorConfig.java
└── Main.java                 # Point d'entrée
```

---

## 3. MILESTONE 1 : ANALYSEUR DE TEMPLATE

### Objectif

Analyser un fichier PPTX template et extraire une structure JSON décrivant :
- Le thème (couleurs, polices)
- Tous les layouts (`/ppt/slideLayouts/*.xml`)
- Chaque zone (placeholders, positions, styles)
- L'association slides réels → layouts

### 🔴 PRINCIPE FONDAMENTAL

L'analyseur doit **exclusivement** analyser les **layouts** (slideLayouts), PAS les slides réels.

```
TEMPLATE.PPTX
    ↓
1. Parcourir /ppt/slideLayouts/          ← ANALYSE PRINCIPALE
    ├── slideLayout1.xml  → Layout 1 (TITLE_SLIDE)
    ├── slideLayout2.xml  → Layout 2 (CONTENT)
    └── slideLayout3.xml  → Layout 3 (SECTION_HEADER)
    ↓
2. Parcourir /ppt/slides/                ← ASSOCIATION UNIQUEMENT
    ├── slide1.xml  → Utilise Layout 1
    ├── slide2.xml  → Utilise Layout 2
    └── slide3.xml  → Utilise Layout 1
    ↓
3. Générer JSON avec :
    ├── layouts (structure des layouts)
    └── model_slides (association slide → layout)
```

### Structure JSON de sortie

```json
{
  "theme": {
    "colors": {
      "primary": "#42B3BD",
      "secondary": "#51B851",
      "background": "#FFFFFF",
      "accent1": "#42B3BD",
      "accent2": "#51B851",
      "text_primary": "#000000",
      "text_secondary": "#131523"
    },
    "fonts": {
      "title": {
        "family": "Aharoni",
        "weight": "Bold",
        "size_pt": 42,
        "color_ref": "primary"
      },
      "subtitle": {
        "family": "Aharoni",
        "weight": "Regular",
        "size_pt": 32,
        "color_ref": "text_secondary"
      },
      "body": {
        "family": "Avenir Next LT Pro Light",
        "weight": "Regular",
        "size_pt": 26,
        "color_ref": "text_primary"
      },
      "caption": {
        "family": "Avenir Next LT Pro Light",
        "weight": "Regular",
        "size_pt": 20,
        "color_ref": "text_secondary"
      }
    }
  },
  "slide_dimensions": {
    "width": 12192000,
    "width_inches": 13.333,
    "height": 6858000,
    "height_inches": 7.5,
    "unit": "EMU"
  },
  "layouts": [
    {
      "layout_id": "layout_0",
      "original_name": "/ppt/slideLayouts/slideLayout8.xml",
      "model_slide": "/ppt/slides/slide8.xml",
      "semantic_type": "SECTION_HEADER",
      "description": "Layout détecté depuis le template PPTX",
      "zones": [
        {
          "zone_id": 0,
          "zone_type": "title",
          "position": "top_center",
          "width_emu": 9144000,
          "height_emu": 1280160,
          "width_inches": 10.0,
          "height_inches": 1.4,
          "x_emu": 1524000,
          "y_emu": 0,
          "surface_percentage": 14.0,
          "style": {
            "font_family": "Aharoni",
            "font_weight": "Bold",
            "font_size_pt": 42,
            "color": "#42B3BD",
            "alignment": "CENTER"
          },
          "placeholder": {
            "type": "title",
            "idx": 0,
            "name": "Titre 18",
            "has_text": false
          },
          "reading_order": 1,
          "importance": "HIGH"
        },
        {
          "zone_id": 1,
          "zone_type": "body",
          "position": "bottom_left",
          "width_emu": 4334256,
          "height_emu": 3017520,
          "width_inches": 4.739,
          "height_inches": 3.3,
          "x_emu": 1524000,
          "y_emu": 2926080,
          "surface_percentage": 15.642,
          "style": {
            "font_family": "Avenir Next LT Pro Light",
            "font_weight": "Regular",
            "font_size_pt": 26,
            "color": "#000000",
            "alignment": "LEFT"
          },
          "placeholder": {
            "type": "obj",
            "idx": 14,
            "name": "Espace réservé du contenu 9",
            "has_text": false
          },
          "reading_order": 2,
          "importance": "MEDIUM"
        }
      ],
      "structural_info": {
        "has_header_bar": false,
        "has_footer": true,
        "has_slide_numbers": true,
        "has_logo": false,
        "logo_position": null
      }
    }
  ],
  "model_slides": [
    {
      "part_name": "/ppt/slides/slide1.xml",
      "layout_id": "layout_6",
      "semantic_type": "TITLE_SLIDE",
      "placeholders": [
        {
          "type": "title",
          "idx": 0,
          "name": "Titre 18",
          "has_text": false,
          "is_image": false
        }
      ],
      "pictures": [
        {
          "embed": "rId3",
          "name": "Espace réservé d'image 9"
        }
      ],
      "has_real_content": true,
      "real_content": {
        "title": "Présentation des ventes 2025"
      }
    }
  ],
  "structural_elements": {
    "has_header_bar": false,
    "has_footer": true,
    "has_slide_numbers": true,
    "has_logo": false,
    "logo_position": null
  },
  "metadata": {
    "analysis_version": "1.0.0",
    "template_original_name": "template.pptx",
    "slide_count": 13,
    "layout_count": 13,
    "analysis_date": "2026-03-17T10:30:00Z"
  }
}
```

### Modèles de données Java

```java
// TemplateStructure.java
public class TemplateStructure {
    private Theme theme;
    private SlideDimensions slideDimensions;
    private List<SlideLayout> layouts;
    private List<ModelSlide> modelSlides;
    private StructuralElements structuralElements;
    private Metadata metadata;
    // getters/setters
}

// Theme.java
public class Theme {
    private ThemeColors colors;
    private ThemeFonts fonts;
    // getters/setters
}

// ThemeColors.java
public class ThemeColors {
    private String primary;
    private String secondary;
    private String background;
    private String accent1;
    private String accent2;
    private String textPrimary;
    private String textSecondary;
    // getters/setters
}

// ThemeFonts.java
public class ThemeFonts {
    private FontStyle title;
    private FontStyle subtitle;
    private FontStyle body;
    private FontStyle caption;
    // getters/setters
}

// FontStyle.java
public class FontStyle {
    private String family;
    private String weight; // "Bold", "Regular", etc.
    private int sizePt;
    private String colorRef; // Référence à une couleur du thème
    // getters/setters
}

// SlideLayout.java
public class SlideLayout {
    private String layoutId;
    private String originalName;
    private String modelSlide;
    private String semanticType; // "TITLE_SLIDE", "SECTION_HEADER", "CONTENT"
    private String description;
    private List<Zone> zones;
    private StructuralInfo structuralInfo;
    // getters/setters
}

// Zone.java
public class Zone {
    private int zoneId;
    private String zoneType; // "title", "body", "subtitle", "picture", "footer", "center_title", "table", "chart"
    private String position; // "top_center", "bottom_left", "middle_right", etc.
    private long widthEmu;
    private long heightEmu;
    private double widthInches;
    private double heightInches;
    private long xEmu;
    private long yEmu;
    private double surfacePercentage;
    private ZoneStyle style;
    private PlaceholderInfo placeholder;
    private ImageInfo imageInfo;
    private TableInfo tableInfo;
    private ChartInfo chartInfo;
    private int readingOrder;
    private String importance; // "HIGH", "MEDIUM", "LOW"
    // getters/setters
}

// ZoneStyle.java
public class ZoneStyle {
    private String fontFamily;
    private String fontWeight;
    private int fontSizePt;
    private String color;
    private String alignment; // "LEFT", "CENTER", "RIGHT", "JUSTIFY"
    // getters/setters
}

// PlaceholderInfo.java
public class PlaceholderInfo {
    private String type; // "title", "subTitle", "obj", "picture", "ctrTitle"
    private int idx;
    private String name;
    private boolean hasText;
    // getters/setters
}

// ImageInfo.java
public class ImageInfo {
    private String embed;
    private String name;
    private boolean isPlaceholder;
    // getters/setters
}

// TableInfo.java
public class TableInfo {
    private int rows;
    private int columns;
    private boolean hasHeader;
    private TableHeaderStyle headerStyle;
    private int dataRows;
    // getters/setters
}

// ChartInfo.java
public class ChartInfo {
    private String chartType; // "bar", "line", "pie", "doughnut", "scatter", "radar"
    private boolean hasLegend;
    private boolean hasTitle;
    private int seriesCount;
    private int categoryCount;
    // getters/setters
}

// ModelSlide.java
public class ModelSlide {
    private String partName;
    private String layoutId;
    private String semanticType;
    private List<PlaceholderInfo> placeholders;
    private List<PictureInfo> pictures;
    private boolean hasRealContent;
    private Map<String, String> realContent; // Données d'exemple
    // getters/setters
}
```

### Implémentation de l'analyseur

```java
public class TemplateAnalyzer {
    
    private static final double EMU_PER_INCH = 914400.0;
    private static final Logger logger = LoggerFactory.getLogger(TemplateAnalyzer.class);
    
    public TemplateStructure analyze(InputStream templateStream) throws Exception {
        PresentationMLPackage presentation = PresentationMLPackage.load(templateStream);
        return analyze(presentation);
    }
    
    public TemplateStructure analyze(String templatePath) throws Exception {
        PresentationMLPackage presentation = PresentationMLPackage.load(new File(templatePath));
        return analyze(presentation);
    }
    
    private TemplateStructure analyze(PresentationMLPackage presentation) throws Exception {
        TemplateStructure structure = new TemplateStructure();
        
        // 1. Extraire le thème
        structure.setTheme(extractTheme(presentation));
        
        // 2. Extraire les dimensions
        structure.setSlideDimensions(extractDimensions(presentation));
        
        // 3. ANALYSE PRINCIPALE : Les layouts
        List<SlideLayout> layouts = analyzeAllLayouts(presentation);
        structure.setLayouts(layouts);
        
        // 4. ASSOCIATION UNIQUEMENT : Les slides réels
        List<ModelSlide> modelSlides = associateSlidesWithLayouts(presentation, layouts);
        structure.setModelSlides(modelSlides);
        
        // 5. Éléments structurels
        structure.setStructuralElements(extractStructuralElements(presentation));
        
        // 6. Métadonnées
        structure.setMetadata(createMetadata(presentation));
        
        return structure;
    }
    
    /**
     * ANALYSE PRINCIPALE : Parcourt UNIQUEMENT les layouts
     */
    private List<SlideLayout> analyzeAllLayouts(PresentationMLPackage presentation) {
        List<SlideLayout> layouts = new ArrayList<>();
        
        // 🔴 On récupère UNIQUEMENT les layouts
        List<SlideLayoutPart> layoutParts = presentation.getParts()
            .getPartsByType(SlideLayoutPart.class);
        
        logger.info("Analyse de {} layouts trouvés", layoutParts.size());
        
        for (SlideLayoutPart layoutPart : layoutParts) {
            try {
                SlideLayout layout = analyzeSingleLayout(layoutPart);
                layouts.add(layout);
                logger.debug("Layout analysé: {}", layout.getLayoutId());
            } catch (Exception e) {
                logger.error("Erreur lors de l'analyse du layout {}", layoutPart.getPartName(), e);
            }
        }
        
        return layouts;
    }
    
    /**
     * Analyse un SEUL layout
     */
    private SlideLayout analyzeSingleLayout(SlideLayoutPart layoutPart) {
        SlideLayout layout = new SlideLayout();
        layout.setLayoutId(generateLayoutId(layoutPart));
        layout.setOriginalName(layoutPart.getPartName().getName());
        
        // Déterminer le type sémantique
        layout.setSemanticType(detectSemanticType(layoutPart));
        layout.setDescription("Layout détecté depuis le template PPTX");
        
        // Analyser les zones
        List<Zone> zones = new ArrayList<>();
        int zoneId = 0;
        
        // Parcourir toutes les shapes du layout
        for (Object obj : getShapesFromLayout(layoutPart)) {
            if (obj instanceof Shape) {
                Shape shape = (Shape) obj;
                
                // 🔴 On analyse UNIQUEMENT les placeholders
                if (isPlaceholder(shape)) {
                    Zone zone = analyzePlaceholderShape(shape, zoneId++);
                    if (zone != null) {
                        zones.add(zone);
                    }
                }
                // Les shapes sans placeholder sont ignorées
            }
        }
        
        // Trier les zones par position (top → bottom, left → right)
        sortZonesByReadingOrder(zones);
        layout.setZones(zones);
        
        // Informations structurelles
        layout.setStructuralInfo(extractStructuralInfo(layoutPart));
        
        // Trouver le slide modèle associé
        layout.setModelSlide(findModelSlide(layoutPart));
        
        return layout;
    }
    
    /**
     * Vérifie si une shape est un placeholder
     */
    private boolean isPlaceholder(Shape shape) {
        // Méthode 1 : Vérifier la propriété placeholder explicite
        if (shape.getPlaceholder() != null) {
            return true;
        }
        
        // Méthode 2 : Vérifier les propriétés XML
        try {
            // Recherche de l'élément <p:ph> dans les propriétés
            // via l'API docx4j
            if (shape.getProperties() != null) {
                // Vérification des propriétés personnalisées
                // (implémentation dépendante de docx4j)
                if (hasPlaceholderProperty(shape)) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.warn("Erreur lors de la vérification du placeholder", e);
        }
        
        return false;
    }
    
    /**
     * Extrait les informations du placeholder
     */
    private Zone analyzePlaceholderShape(Shape shape, int zoneId) {
        Zone zone = new Zone();
        zone.setZoneId(zoneId);
        
        // 1. Type du placeholder
        PlaceholderInfo placeholder = new PlaceholderInfo();
        if (shape.getPlaceholder() != null) {
            String type = shape.getPlaceholder().getType().toString();
            placeholder.setType(type);
            placeholder.setIdx(shape.getPlaceholder().getIdx());
            placeholder.setName(shape.getName());
            placeholder.setHasText(shape.hasText());
            
            zone.setPlaceholder(placeholder);
            zone.setZoneType(mapPlaceholderTypeToZoneType(type));
        } else {
            // Si c'est un placeholder sans type explicite
            zone.setZoneType("body");
        }
        
        // 2. Position et dimensions
        zone.setXEmu(shape.getX());
        zone.setYEmu(shape.getY());
        zone.setWidthEmu(shape.getW());
        zone.setHeightEmu(shape.getH());
        
        zone.setWidthInches(shape.getW() / EMU_PER_INCH);
        zone.setHeightInches(shape.getH() / EMU_PER_INCH);
        zone.setXEmu(shape.getX());
        zone.setYEmu(shape.getY());
        
        // 3. Surface percentage
        double totalSurface = getSlideTotalSurface();
        double zoneSurface = shape.getW() * shape.getH();
        zone.setSurfacePercentage((zoneSurface / totalSurface) * 100);
        
        // 4. Position relative
        zone.setPosition(determinePosition(shape));
        
        // 5. Style
        if (shape.getTextStyle() != null) {
            ZoneStyle style = extractStyle(shape);
            zone.setStyle(style);
        }
        
        // 6. Image info
        if (isImagePlaceholder(shape)) {
            ImageInfo imageInfo = extractImageInfo(shape);
            zone.setImageInfo(imageInfo);
        }
        
        // 7. Importance
        zone.setImportance(determineImportance(zone));
        
        // 8. Reading order
        zone.setReadingOrder(zoneId + 1);
        
        return zone;
    }
    
    /**
     * ASSOCIATION UNIQUEMENT : Associe les slides réels aux layouts
     */
    private List<ModelSlide> associateSlidesWithLayouts(PresentationMLPackage presentation, 
                                                        List<SlideLayout> layouts) {
        List<ModelSlide> modelSlides = new ArrayList<>();
        
        // On récupère les slides réels
        List<SlidePart> slideParts = presentation.getParts()
            .getPartsByType(SlidePart.class);
        
        for (SlidePart slidePart : slideParts) {
            ModelSlide modelSlide = new ModelSlide();
            modelSlide.setPartName(slidePart.getPartName().getName());
            
            // 🔴 On trouve LE layout associé à ce slide
            String layoutId = findLayoutIdForSlide(slidePart, layouts);
            modelSlide.setLayoutId(layoutId);
            
            // Déterminer le type sémantique
            modelSlide.setSemanticType(determineSemanticTypeForSlide(slidePart));
            
            // Extraire les placeholders du slide (pour info)
            List<PlaceholderInfo> placeholders = extractPlaceholdersFromSlide(slidePart);
            modelSlide.setPlaceholders(placeholders);
            
            // Extraire les images (pour info)
            List<PictureInfo> pictures = extractPicturesFromSlide(slidePart);
            modelSlide.setPictures(pictures);
            
            // Optionnel : extraire le contenu réel (pour référence)
            Map<String, String> realContent = extractRealContent(slidePart);
            if (!realContent.isEmpty()) {
                modelSlide.setHasRealContent(true);
                modelSlide.setRealContent(realContent);
            }
            
            modelSlides.add(modelSlide);
        }
        
        return modelSlides;
    }
    
    /**
     * Méthodes utilitaires
     */
    private String generateLayoutId(SlideLayoutPart layoutPart) {
        String name = layoutPart.getPartName().getName();
        // Extraire le numéro du layout
        Pattern pattern = Pattern.compile("slideLayout(\\d+)\\.xml");
        Matcher matcher = pattern.matcher(name);
        if (matcher.find()) {
            return "layout_" + matcher.group(1);
        }
        return "layout_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private String detectSemanticType(SlideLayoutPart layoutPart) {
        // Analyse les placeholders pour déterminer le type
        boolean hasCtrTitle = false;
        boolean hasTitle = false;
        boolean hasSubTitle = false;
        boolean hasBody = false;
        boolean hasPicture = false;
        
        for (Object obj : getShapesFromLayout(layoutPart)) {
            if (obj instanceof Shape) {
                Shape shape = (Shape) obj;
                if (isPlaceholder(shape) && shape.getPlaceholder() != null) {
                    String type = shape.getPlaceholder().getType().toString();
                    switch (type) {
                        case "ctrTitle": hasCtrTitle = true; break;
                        case "title": hasTitle = true; break;
                        case "subTitle": hasSubTitle = true; break;
                        case "body": hasBody = true; break;
                        case "obj": hasBody = true; break;
                        case "pic": hasPicture = true; break;
                    }
                }
            }
        }
        
        if (hasCtrTitle) return "TITLE_SLIDE";
        if (hasTitle && hasSubTitle) return "TITLE_SLIDE";
        if (hasTitle && hasPicture && !hasBody) return "SECTION_HEADER";
        if (hasTitle && hasBody) return "CONTENT";
        if (hasTitle && !hasBody) return "SECTION_HEADER";
        
        return "CONTENT";
    }
    
    private String mapPlaceholderTypeToZoneType(String placeholderType) {
        switch (placeholderType) {
            case "title": return "title";
            case "ctrTitle": return "center_title";
            case "subTitle": return "subtitle";
            case "body": return "body";
            case "obj": return "body";
            case "pic": return "picture";
            default: return "body";
        }
    }
    
    private String determinePosition(Shape shape) {
        double x = shape.getX();
        double y = shape.getY();
        double width = shape.getW();
        double height = shape.getH();
        double slideWidth = getSlideWidth();
        double slideHeight = getSlideHeight();
        
        // Position horizontale
        String horizontal;
        if (x < slideWidth * 0.2) {
            horizontal = "left";
        } else if (x + width > slideWidth * 0.8) {
            horizontal = "right";
        } else {
            horizontal = "center";
        }
        
        // Position verticale
        String vertical;
        if (y < slideHeight * 0.2) {
            vertical = "top";
        } else if (y + height > slideHeight * 0.8) {
            vertical = "bottom";
        } else {
            vertical = "middle";
        }
        
        return vertical + "_" + horizontal;
    }
    
    private ZoneStyle extractStyle(Shape shape) {
        ZoneStyle style = new ZoneStyle();
        // Extraction des styles depuis la shape
        // (implémentation dépendante de docx4j)
        return style;
    }
    
    private String determineImportance(Zone zone) {
        // HIGH : title, center_title, picture grande surface
        if (zone.getZoneType().equals("title") || zone.getZoneType().equals("center_title")) {
            return "HIGH";
        }
        if (zone.getZoneType().equals("picture") && zone.getSurfacePercentage() > 25) {
            return "HIGH";
        }
        // MEDIUM : body, subtitle, picture moyenne
        if (zone.getZoneType().equals("body") || zone.getZoneType().equals("subtitle")) {
            return "MEDIUM";
        }
        // LOW : footer, autres
        return "LOW";
    }
    
    private String findLayoutIdForSlide(SlidePart slidePart, List<SlideLayout> layouts) {
        // Extraire l'ID du layout depuis le slide
        String layoutName = extractLayoutNameFromSlide(slidePart);
        if (layoutName != null) {
            for (SlideLayout layout : layouts) {
                if (layout.getOriginalName().contains(layoutName)) {
                    return layout.getLayoutId();
                }
            }
        }
        return null;
    }
    
    private long convertToEmu(double inches) {
        return Math.round(inches * EMU_PER_INCH);
    }
    
    private double convertToInches(long emu) {
        return emu / EMU_PER_INCH;
    }
}
```

### Tests unitaires

```java
public class TemplateAnalyzerTest {
    
    @Test
    public void testAnalyzeTemplate() throws Exception {
        TemplateAnalyzer analyzer = new TemplateAnalyzer();
        TemplateStructure structure = analyzer.analyze("src/test/resources/template.pptx");
        
        // Vérifications
        assertNotNull(structure);
        assertNotNull(structure.getTheme());
        assertNotNull(structure.getTheme().getColors());
        assertNotNull(structure.getTheme().getColors().getPrimary());
        
        assertNotNull(structure.getLayouts());
        assertTrue(structure.getLayouts().size() > 0);
        
        // Vérifier qu'au moins un layout a des zones
        SlideLayout firstLayout = structure.getLayouts().get(0);
        assertNotNull(firstLayout.getZones());
        assertTrue(firstLayout.getZones().size() > 0);
        
        // Vérifier les zones
        for (SlideLayout layout : structure.getLayouts()) {
            for (Zone zone : layout.getZones()) {
                assertNotNull(zone.getZoneType());
                assertTrue(zone.getWidthEmu() > 0);
                assertTrue(zone.getHeightEmu() > 0);
            }
        }
        
        // Vérifier les model slides
        assertNotNull(structure.getModelSlides());
        
        // Sauvegarder le JSON
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File("target/template-structure.json"), structure);
        
        // Vérifier que le JSON peut être relu
        String json = mapper.writeValueAsString(structure);
        TemplateStructure reloaded = mapper.readValue(json, TemplateStructure.class);
        assertEquals(structure.getLayouts().size(), reloaded.getLayouts().size());
    }
    
    @Test
    public void testDetectSemanticType() throws Exception {
        TemplateAnalyzer analyzer = new TemplateAnalyzer();
        TemplateStructure structure = analyzer.analyze("src/test/resources/template.pptx");
        
        // Vérifier les types sémantiques
        for (SlideLayout layout : structure.getLayouts()) {
            String type = layout.getSemanticType();
            assertTrue(type.equals("TITLE_SLIDE") || 
                       type.equals("SECTION_HEADER") || 
                       type.equals("CONTENT"));
        }
    }
}
```

---

## 4. MILESTONE 2 : PLANIFICATEUR DE PRÉSENTATION

### Objectif

Générer un plan de présentation structuré via IA, basé sur :
- Un sujet
- Un public cible
- Un ton
- Des contraintes (nombre de slides, types requis)

### PlanRequest (entrée)

```json
{
  "topic": "Évolution de la population mondiale",
  "audience": "Étudiants en démographie",
  "tone": "professionnel et éducatif",
  "slideCount": 8,
  "constraints": [
    "Inclure au moins un graphique",
    "Inclure un tableau de données",
    "Max 5 points par slide",
    "Style épuré et lisible"
  ],
  "availableLayouts": [
    "TITLE_SLIDE",
    "SECTION_HEADER",
    "CONTENT"
  ]
}
```

### PresentationPlan (sortie)

```json
{
  "presentationTitle": "Évolution de la population mondiale",
  "topic": "Évolution de la population mondiale",
  "audience": "Étudiants en démographie",
  "tone": "professionnel et éducatif",
  "slides": [
    {
      "id": "slide_1",
      "type": "cover",
      "title": "Évolution de la population mondiale",
      "subtitle": "Analyse démographique 1950-2050",
      "layoutPreference": "TITLE_SLIDE"
    },
    {
      "id": "slide_2",
      "type": "agenda",
      "title": "Plan de la présentation",
      "items": [
        "Introduction à la démographie",
        "Évolution historique",
        "Facteurs clés",
        "Projections futures",
        "Conclusion"
      ],
      "layoutPreference": "CONTENT"
    },
    {
      "id": "slide_3",
      "type": "section_header",
      "title": "Introduction à la démographie",
      "sectionName": "Contexte",
      "layoutPreference": "SECTION_HEADER"
    },
    {
      "id": "slide_4",
      "type": "content",
      "title": "Qu'est-ce que la démographie ?",
      "contentType": "text",
      "layoutPreference": "CONTENT",
      "dataRequirements": [
        {"zoneType": "title", "description": "Titre court"},
        {"zoneType": "body", "description": "Définition et contexte"}
      ]
    },
    {
      "id": "slide_5",
      "type": "section_header",
      "title": "Évolution historique",
      "sectionName": "Données clés",
      "layoutPreference": "SECTION_HEADER"
    },
    {
      "id": "slide_6",
      "type": "content_with_chart",
      "title": "Évolution historique de la population",
      "contentType": "chart",
      "layoutPreference": "CONTENT",
      "chartRequirements": {
        "type": "line",
        "title": "Population mondiale (en milliards)",
        "xAxis": "Année",
        "yAxis": "Population en milliards",
        "expectedCategories": ["1950", "1960", "1970", "1980", "1990", "2000", "2010", "2020"],
        "expectedSeries": [
          {"name": "Population", "values": []}
        ]
      },
      "dataRequirements": [
        {"zoneType": "title", "description": "Titre du slide"},
        {"zoneType": "chart", "description": "Graphique d'évolution"}
      ]
    },
    {
      "id": "slide_7",
      "type": "content_with_table",
      "title": "Top 10 des pays les plus peuplés",
      "contentType": "table",
      "layoutPreference": "CONTENT",
      "tableRequirements": {
        "columns": ["Rang", "Pays", "Population (millions)", "Continent"],
        "hasHeader": true,
        "rowCount": 10
      },
      "dataRequirements": [
        {"zoneType": "title", "description": "Titre du slide"},
        {"zoneType": "table", "description": "Tableau des populations"}
      ]
    },
    {
      "id": "slide_8",
      "type": "summary",
      "title": "Points clés à retenir",
      "items": [
        "La population mondiale a quadruplé en 100 ans",
        "La croissance ralentit mais continue",
        "L'Afrique sera le moteur de la croissance future"
      ],
      "layoutPreference": "CONTENT"
    }
  ]
}
```

### Modèles de données

```java
// PlanRequest.java
public class PlanRequest {
    private String topic;
    private String audience;
    private String tone;
    private int slideCount;
    private List<String> constraints;
    private List<String> availableLayouts; // "TITLE_SLIDE", "SECTION_HEADER", "CONTENT"
    // getters/setters
}

// PresentationPlan.java
public class PresentationPlan {
    private String presentationTitle;
    private String topic;
    private String audience;
    private String tone;
    private List<PlanSlide> slides;
    // getters/setters
}

// PlanSlide.java
public class PlanSlide {
    private String id;
    private String type; // "cover", "agenda", "section_header", "content", "content_with_chart", "content_with_table", "summary"
    private String title;
    private String subtitle;
    private List<String> items;
    private String sectionName;
    private String contentType; // "text", "chart", "table"
    private String layoutPreference; // "TITLE_SLIDE", "SECTION_HEADER", "CONTENT"
    private ChartRequirements chartRequirements;
    private TableRequirements tableRequirements;
    private List<DataRequirement> dataRequirements;
    // getters/setters
}

// ChartRequirements.java
public class ChartRequirements {
    private String type; // "bar", "line", "pie", "doughnut", "scatter", "radar"
    private String title;
    private String xAxis;
    private String yAxis;
    private List<String> expectedCategories;
    private List<ChartSeriesRequirement> expectedSeries;
    // getters/setters
}

// TableRequirements.java
public class TableRequirements {
    private List<String> columns;
    private boolean hasHeader;
    private int rowCount;
    // getters/setters
}
```

### Implémentation du planificateur

```java
public class PresentationPlanner {
    
    private final AIProvider aiProvider;
    private final ObjectMapper mapper;
    private static final Logger logger = LoggerFactory.getLogger(PresentationPlanner.class);
    
    public PresentationPlanner(AIProvider aiProvider) {
        this.aiProvider = aiProvider;
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    public PresentationPlan generatePlan(PlanRequest request) throws Exception {
        logger.info("Génération du plan pour: {}", request.getTopic());
        
        // 1. Construire le prompt
        String prompt = buildPrompt(request);
        
        // 2. Appeler l'IA
        String systemPrompt = getSystemPrompt();
        String response = aiProvider.callAI(systemPrompt, prompt);
        
        // 3. Parser la réponse
        PresentationPlan plan = parseResponse(response);
        
        // 4. Valider le plan
        validatePlan(plan, request);
        
        logger.info("Plan généré avec {} slides", plan.getSlides().size());
        return plan;
    }
    
    private String getSystemPrompt() {
        return """
            Tu es un expert en conception de présentations professionnelles.
            Tu dois créer des plans de présentation structurés, clairs et adaptés au public.
            Les présentations doivent être bien organisées, avec une progression logique.
            Tu dois varier les types de slides pour maintenir l'attention.
            """;
    }
    
    private String buildPrompt(PlanRequest request) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Crée un plan de présentation détaillé avec les caractéristiques suivantes :\n\n");
        prompt.append("📌 SUJET : ").append(request.getTopic()).append("\n");
        prompt.append("👥 PUBLIC : ").append(request.getAudience()).append("\n");
        prompt.append("🎯 TON : ").append(request.getTone()).append("\n");
        prompt.append("📊 NOMBRE DE SLIDES : ").append(request.getSlideCount()).append("\n\n");
        
        if (request.getConstraints() != null && !request.getConstraints().isEmpty()) {
            prompt.append("🔒 CONTRAINTES :\n");
            for (String constraint : request.getConstraints()) {
                prompt.append("   - ").append(constraint).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("📐 LAYOUTS DISPONIBLES :\n");
        prompt.append("   - TITLE_SLIDE : Page de garde\n");
        prompt.append("   - SECTION_HEADER : Introduction de section\n");
        prompt.append("   - CONTENT : Slide de contenu (texte, tableau, graphique)\n\n");
        
        prompt.append("📋 TYPES DE SLIDES À UTILISER :\n");
        prompt.append("   - cover : Page de garde\n");
        prompt.append("   - agenda : Sommaire\n");
        prompt.append("   - section_header : Introduction de section\n");
        prompt.append("   - content : Contenu textuel\n");
        prompt.append("   - content_with_chart : Contenu avec graphique\n");
        prompt.append("   - content_with_table : Contenu avec tableau\n");
        prompt.append("   - summary : Résumé final\n\n");
        
        prompt.append("📝 RÉPONDS AVEC UN JSON STRICT SELON CE SCHÉMA :\n");
        prompt.append("""
            {
              "presentationTitle": "titre principal",
              "slides": [
                {
                  "id": "slide_1",
                  "type": "cover|agenda|section_header|content|content_with_chart|content_with_table|summary",
                  "title": "titre du slide",
                  "subtitle": "sous-titre (pour cover)",
                  "items": ["point1", "point2"] (pour agenda et summary),
                  "sectionName": "nom de la section (pour section_header)",
                  "contentType": "text|chart|table",
                  "layoutPreference": "TITLE_SLIDE|SECTION_HEADER|CONTENT",
                  "chartRequirements": {
                    "type": "bar|line|pie",
                    "title": "titre du graphique",
                    "xAxis": "axe X",
                    "yAxis": "axe Y",
                    "expectedCategories": ["Cat1", "Cat2"],
                    "expectedSeries": [{"name": "Série", "values": []}]
                  },
                  "tableRequirements": {
                    "columns": ["Col1", "Col2"],
                    "hasHeader": true,
                    "rowCount": 5
                  },
                  "dataRequirements": [
                    {"zoneType": "title", "description": "description"},
                    {"zoneType": "body", "description": "description"}
                  ]
                }
              ]
            }
            """);
        
        prompt.append("\n🔴 CONSIGNES IMPORTANTES :\n");
        prompt.append("   - Toujours commencer par une slide de type 'cover'\n");
        prompt.append("   - Toujours inclure une slide de type 'agenda' en 2ème position\n");
        prompt.append("   - Terminer par une slide de type 'summary'\n");
        prompt.append("   - Varier les types de slides\n");
        prompt.append("   - Chaque slide doit avoir un titre clair et concis\n");
        prompt.append("   - Les titres doivent être courts (< 10 mots)\n");
        prompt.append("   - Le nombre total de slides doit être de ").append(request.getSlideCount()).append("\n");
        prompt.append("   - Les slides de type 'section_header' servent à introduire de nouvelles sections\n");
        prompt.append("   - Inclure au moins un graphique et un tableau si possible\n");
        prompt.append("   - Répondre UNIQUEMENT avec le JSON, sans commentaires\n");
        
        return prompt.toString();
    }
    
    private PresentationPlan parseResponse(String response) throws Exception {
        // Nettoyer la réponse (enlever les markdown s'il y en a)
        String cleanResponse = response.trim();
        if (cleanResponse.startsWith("```json")) {
            cleanResponse = cleanResponse.substring(7);
        }
        if (cleanResponse.startsWith("```")) {
            cleanResponse = cleanResponse.substring(3);
        }
        if (cleanResponse.endsWith("```")) {
            cleanResponse = cleanResponse.substring(0, cleanResponse.length() - 3);
        }
        cleanResponse = cleanResponse.trim();
        
        // Parser le JSON
        return mapper.readValue(cleanResponse, PresentationPlan.class);
    }
    
    private void validatePlan(PresentationPlan plan, PlanRequest request) {
        List<PlanSlide> slides = plan.getSlides();
        
        // Vérifier qu'il y a au moins une slide
        if (slides.isEmpty()) {
            throw new IllegalArgumentException("Le plan doit contenir au moins une slide");
        }
        
        // Vérifier que la première slide est une cover
        if (!slides.get(0).getType().equals("cover")) {
            logger.warn("La première slide devrait être de type 'cover'");
        }
        
        // Vérifier que la dernière slide est un summary
        PlanSlide lastSlide = slides.get(slides.size() - 1);
        if (!lastSlide.getType().equals("summary")) {
            logger.warn("La dernière slide devrait être de type 'summary'");
        }
        
        // Vérifier que l'agenda est en 2ème position (ou au moins présent)
        boolean hasAgenda = slides.stream().anyMatch(s -> s.getType().equals("agenda"));
        if (!hasAgenda) {
            logger.warn("Le plan devrait contenir une slide de type 'agenda'");
        }
        
        // Vérifier le nombre de slides
        if (slides.size() != request.getSlideCount()) {
            logger.warn("Nombre de slides attendu: {}, obtenu: {}", 
                       request.getSlideCount(), slides.size());
        }
        
        // Vérifier que chaque slide a un titre
        for (PlanSlide slide : slides) {
            if (slide.getTitle() == null || slide.getTitle().isEmpty()) {
                throw new IllegalArgumentException("Slide " + slide.getId() + " n'a pas de titre");
            }
        }
    }
}
```

### AIProvider

```java
public class AIProvider {
    
    private final String apiKey;
    private final String model;
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    
    public AIProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.client = new OkHttpClient();
        this.mapper = new ObjectMapper();
    }
    
    public String callAI(String systemPrompt, String userPrompt) throws Exception {
        // Construction de la requête
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        ));
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 4000);
        
        String json = mapper.writeValueAsString(requestBody);
        
        Request request = new Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .post(RequestBody.create(json, MediaType.parse("application/json")))
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Erreur API: " + response.code() + " - " + response.body().string());
            }
            
            String responseBody = response.body().string();
            Map<String, Object> result = mapper.readValue(responseBody, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            
            return (String) message.get("content");
        }
    }
}
```

---

## 5. MILESTONE 3 : LAYOUT ASSIGNER

### Objectif

Associer chaque slide du plan à un layout du template (ou marquer comme dynamique).

### Règles d'assignation

| Type de slide du plan | Layout recherché | Critères |
| :--- | :--- | :--- |
| `cover` | `TITLE_SLIDE` | Contient title + subtitle ou ctrTitle |
| `section_header` | `SECTION_HEADER` | Contient title + picture ou title + body |
| `content` | `CONTENT` | Contient title + body (1 ou plusieurs) |
| `content_with_chart` | `CONTENT` avec zone `chart` | Contient zone de type chart |
| `content_with_table` | `CONTENT` avec zone `table` | Contient zone de type table |
| `agenda` | `CONTENT` avec plusieurs zones body | Au moins 3 zones body |
| `summary` | `CONTENT` | Contient title + body |

### EnrichedPlan (sortie)

```json
{
  "slides": [
    {
      "planSlide": {
        "id": "slide_1",
        "type": "cover",
        "title": "Évolution de la population mondiale"
      },
      "layout": {
        "id": "layout_6",
        "semanticType": "TITLE_SLIDE",
        "slideIndex": 0,
        "isDynamic": false
      },
      "zoneMapping": {
        "zone_title": "title",
        "zone_subtitle": "subtitle"
      },
      "zonesToFill": [
        {
          "zoneId": "zone_title",
          "dataKey": "title",
          "type": "text",
          "expectedContent": "Titre principal"
        },
        {
          "zoneId": "zone_subtitle", 
          "dataKey": "subtitle",
          "type": "text",
          "expectedContent": "Sous-titre"
        }
      ],
      "renderStrategy": "USE_TEMPLATE_LAYOUT"
    },
    {
      "planSlide": {
        "id": "slide_6",
        "type": "content_with_chart",
        "title": "Évolution historique de la population"
      },
      "layout": {
        "id": "layout_4",
        "semanticType": "CONTENT",
        "slideIndex": 3,
        "isDynamic": false
      },
      "zoneMapping": {
        "zone_title": "title",
        "zone_chart": "chart"
      },
      "zonesToFill": [
        {
          "zoneId": "zone_title",
          "dataKey": "title",
          "type": "text",
          "expectedContent": "Titre du slide"
        },
        {
          "zoneId": "zone_chart",
          "dataKey": "chart",
          "type": "chart",
          "expectedContent": "Graphique d'évolution"
        }
      ],
      "renderStrategy": "USE_TEMPLATE_LAYOUT"
    },
    {
      "planSlide": {
        "id": "slide_7",
        "type": "content_with_table",
        "title": "Top 10 des pays les plus peuplés"
      },
      "layout": {
        "isDynamic": true,
        "description": "Aucun layout avec tableau disponible"
      },
      "zoneMapping": {},
      "zonesToFill": [
        {
          "zoneId": "dynamic_title",
          "dataKey": "title",
          "type": "text",
          "expectedContent": "Titre du slide"
        },
        {
          "zoneId": "dynamic_table",
          "dataKey": "table",
          "type": "table",
          "expectedContent": "Tableau des populations"
        }
      ],
      "renderStrategy": "GENERATE_FROM_SCRATCH",
      "dynamicComponents": [
        {
          "id": "dynamic_title",
          "type": "text",
          "position": {"x": 1.0, "y": 0.5, "w": 8.0, "h": 0.8},
          "style": {
            "fontFamily": "Aharoni",
            "fontSize": 42,
            "color": "#42B3BD",
            "alignment": "CENTER"
          }
        },
        {
          "id": "dynamic_table",
          "type": "table",
          "position": {"x": 0.8, "y": 1.8, "w": 8.4, "h": 3.5},
          "tableConfig": {
            "columns": ["Rang", "Pays", "Population (millions)", "Continent"],
            "hasHeader": true,
            "rowCount": 10
          }
        }
      ]
    }
  ]
}
```

### Modèles de données

```java
// EnrichedPlan.java
public class EnrichedPlan {
    private List<EnrichedSlide> slides;
    // getters/setters
}

// EnrichedSlide.java
public class EnrichedSlide {
    private PlanSlide planSlide;
    private LayoutAssignment layout;
    private Map<String, String> zoneMapping; // zoneId → dataKey
    private List<ZoneToFill> zonesToFill;
    private RenderStrategy renderStrategy; // "USE_TEMPLATE_LAYOUT" | "GENERATE_FROM_SCRATCH"
    private List<DynamicComponent> dynamicComponents; // Pour les slides dynamiques
    private int slideIndex;
    // getters/setters
}

// LayoutAssignment.java
public class LayoutAssignment {
    private String id;
    private String semanticType;
    private int slideIndex;
    private boolean isDynamic;
    private String description;
    // getters/setters
}

// ZoneToFill.java
public class ZoneToFill {
    private String zoneId;
    private String dataKey;
    private String type; // "text", "chart", "table", "image"
    private String expectedContent;
    // getters/setters
}

// DynamicComponent.java
public class DynamicComponent {
    private String id;
    private String type; // "text", "chart", "table"
    private Position position;
    private ZoneStyle style;
    private TableConfig tableConfig;
    private ChartConfig chartConfig;
    // getters/setters
}

// Position.java
public class Position {
    private double x;
    private double y;
    private double w;
    private double h;
    // getters/setters
}

// RenderStrategy.java
public enum RenderStrategy {
    USE_TEMPLATE_LAYOUT,
    GENERATE_FROM_SCRATCH
}
```

### Implémentation du Layout Assigner

```java
public class LayoutAssigner {
    
    private static final Logger logger = LoggerFactory.getLogger(LayoutAssigner.class);
    
    public EnrichedPlan assignLayouts(TemplateStructure template, PresentationPlan plan) {
        logger.info("Assignation des layouts pour {} slides", plan.getSlides().size());
        
        EnrichedPlan enrichedPlan = new EnrichedPlan();
        List<EnrichedSlide> enrichedSlides = new ArrayList<>();
        
        for (PlanSlide planSlide : plan.getSlides()) {
            EnrichedSlide enrichedSlide = assignLayoutForSlide(template, planSlide);
            enrichedSlides.add(enrichedSlide);
        }
        
        enrichedPlan.setSlides(enrichedSlides);
        logger.info("Assignation terminée: {} layouts utilisés, {} dynamiques",
                   enrichedSlides.stream().filter(s -> !s.getLayout().isDynamic()).count(),
                   enrichedSlides.stream().filter(s -> s.getLayout().isDynamic()).count());
        
        return enrichedPlan;
    }
    
    private EnrichedSlide assignLayoutForSlide(TemplateStructure template, PlanSlide planSlide) {
        EnrichedSlide enrichedSlide = new EnrichedSlide();
        enrichedSlide.setPlanSlide(planSlide);
        
        // 1. Trouver le meilleur layout
        SlideLayout bestLayout = findBestLayout(template, planSlide);
        
        if (bestLayout != null) {
            // Cas : layout trouvé dans le template
            LayoutAssignment assignment = new LayoutAssignment();
            assignment.setId(bestLayout.getLayoutId());
            assignment.setSemanticType(bestLayout.getSemanticType());
            assignment.setSlideIndex(template.getLayouts().indexOf(bestLayout));
            assignment.setDynamic(false);
            enrichedSlide.setLayout(assignment);
            enrichedSlide.setRenderStrategy(RenderStrategy.USE_TEMPLATE_LAYOUT);
            
            // Créer le mapping zone → data
            Map<String, String> zoneMapping = createZoneMapping(bestLayout, planSlide);
            enrichedSlide.setZoneMapping(zoneMapping);
            
            // Créer la liste des zones à remplir
            List<ZoneToFill> zonesToFill = createZonesToFill(bestLayout, zoneMapping, planSlide);
            enrichedSlide.setZonesToFill(zonesToFill);
            
        } else {
            // Cas : aucun layout adapté → dynamique
            LayoutAssignment assignment = new LayoutAssignment();
            assignment.setDynamic(true);
            assignment.setDescription("Aucun layout adapté trouvé pour le type " + planSlide.getType());
            enrichedSlide.setLayout(assignment);
            enrichedSlide.setRenderStrategy(RenderStrategy.GENERATE_FROM_SCRATCH);
            enrichedSlide.setZoneMapping(new HashMap<>());
            
            // Créer les composants dynamiques
            List<DynamicComponent> components = createDynamicComponents(planSlide);
            enrichedSlide.setDynamicComponents(components);
        }
        
        return enrichedSlide;
    }
    
    private SlideLayout findBestLayout(TemplateStructure template, PlanSlide planSlide) {
        List<SlideLayout> layouts = template.getLayouts();
        
        // Filtrer par type sémantique
        String targetSemanticType = mapPlanTypeToSemanticType(planSlide.getType());
        List<SlideLayout> candidates = layouts.stream()
            .filter(l -> l.getSemanticType().equals(targetSemanticType))
            .collect(Collectors.toList());
        
        if (candidates.isEmpty()) {
            // Fallback : prendre n'importe quel layout
            candidates = layouts;
        }
        
        // Filtrer par zones disponibles
        if (planSlide.getType().equals("content_with_chart")) {
            candidates = candidates.stream()
                .filter(l -> hasZoneOfType(l, "chart"))
                .collect(Collectors.toList());
        }
        
        if (planSlide.getType().equals("content_with_table")) {
            candidates = candidates.stream()
                .filter(l -> hasZoneOfType(l, "table"))
                .collect(Collectors.toList());
        }
        
        if (planSlide.getType().equals("agenda")) {
            // Chercher un layout avec plusieurs zones body
            candidates = candidates.stream()
                .filter(l -> countZonesOfType(l, "body") >= 3)
                .collect(Collectors.toList());
        }
        
        // Calculer un score de similarité
        SlideLayout bestLayout = null;
        double bestScore = -1;
        
        for (SlideLayout layout : candidates) {
            double score = calculateSimilarityScore(layout, planSlide);
            if (score > bestScore) {
                bestScore = score;
                bestLayout = layout;
            }
        }
        
        return bestLayout;
    }
    
    private String mapPlanTypeToSemanticType(String planType) {
        switch (planType) {
            case "cover":
                return "TITLE_SLIDE";
            case "section_header":
                return "SECTION_HEADER";
            case "agenda":
            case "content":
            case "content_with_chart":
            case "content_with_table":
            case "summary":
                return "CONTENT";
            default:
                return "CONTENT";
        }
    }
    
    private boolean hasZoneOfType(SlideLayout layout, String zoneType) {
        return layout.getZones().stream()
            .anyMatch(z -> z.getZoneType().equals(zoneType));
    }
    
    private long countZonesOfType(SlideLayout layout, String zoneType) {
        return layout.getZones().stream()
            .filter(z -> z.getZoneType().equals(zoneType))
            .count();
    }
    
    private double calculateSimilarityScore(SlideLayout layout, PlanSlide planSlide) {
        double score = 0.0;
        
        // 1. Correspondance du nombre de zones
        int expectedZones = planSlide.getDataRequirements() != null ? 
            planSlide.getDataRequirements().size() : 1;
        int actualZones = layout.getZones().size();
        if (actualZones >= expectedZones) {
            score += 0.3;
        }
        
        // 2. Présence des types de zones nécessaires
        if (planSlide.getDataRequirements() != null) {
            for (DataRequirement req : planSlide.getDataRequirements()) {
                boolean hasMatchingZone = layout.getZones().stream()
                    .anyMatch(z -> z.getZoneType().equals(req.getZoneType()));
                if (hasMatchingZone) {
                    score += 0.2;
                }
            }
        }
        
        // 3. Préférence de layout
        if (planSlide.getLayoutPreference() != null) {
            if (layout.getSemanticType().equals(planSlide.getLayoutPreference())) {
                score += 0.2;
            }
        }
        
        return score;
    }
    
    private Map<String, String> createZoneMapping(SlideLayout layout, PlanSlide planSlide) {
        Map<String, String> mapping = new HashMap<>();
        
        // Mapping basé sur les types de zones
        for (Zone zone : layout.getZones()) {
            String dataKey = inferDataKey(zone, planSlide);
            if (dataKey != null) {
                mapping.put(zone.getZoneId() + "_" + zone.getZoneType(), dataKey);
            }
        }
        
        return mapping;
    }
    
    private String inferDataKey(Zone zone, PlanSlide planSlide) {
        String zoneType = zone.getZoneType();
        
        switch (zoneType) {
            case "title":
            case "center_title":
                return "title";
            case "subtitle":
                return "subtitle";
            case "body":
                // Déterminer si c'est le premier body, le deuxième, etc.
                return "body";
            case "picture":
                return "image";
            case "chart":
                return "chart";
            case "table":
                return "table";
            case "footer":
                return "footer";
            default:
                return null;
        }
    }
    
    private List<ZoneToFill> createZonesToFill(SlideLayout layout, Map<String, String> mapping, PlanSlide planSlide) {
        List<ZoneToFill> zonesToFill = new ArrayList<>();
        
        for (Zone zone : layout.getZones()) {
            String zoneKey = zone.getZoneId() + "_" + zone.getZoneType();
            String dataKey = mapping.get(zoneKey);
            
            if (dataKey != null) {
                ZoneToFill toFill = new ZoneToFill();
                toFill.setZoneId(zoneKey);
                toFill.setDataKey(dataKey);
                toFill.setType(zone.getZoneType());
                toFill.setExpectedContent(determineExpectedContent(zone, planSlide));
                zonesToFill.add(toFill);
            }
        }
        
        return zonesToFill;
    }
    
    private String determineExpectedContent(Zone zone, PlanSlide planSlide) {
        String zoneType = zone.getZoneType();
        
        switch (zoneType) {
            case "title":
            case "center_title":
                return "Titre du slide";
            case "subtitle":
                return "Sous-titre explicatif";
            case "body":
                return "Contenu textuel (3-5 phrases)";
            case "chart":
                return "Graphique avec données";
            case "table":
                return "Tableau de données";
            case "picture":
                return "Image illustrative";
            case "footer":
                return "Source ou note";
            default:
                return "Contenu";
        }
    }
    
    private List<DynamicComponent> createDynamicComponents(PlanSlide planSlide) {
        List<DynamicComponent> components = new ArrayList<>();
        
        // 1. Titre (toujours présent)
        DynamicComponent titleComponent = new DynamicComponent();
        titleComponent.setId("dynamic_title");
        titleComponent.setType("text");
        titleComponent.setPosition(new Position(1.0, 0.5, 8.0, 0.8));
        titleComponent.setStyle(new ZoneStyle());
        titleComponent.getStyle().setFontFamily("Arial");
        titleComponent.getStyle().setFontSizePt(42);
        titleComponent.getStyle().setColor("#22223B");
        titleComponent.getStyle().setAlignment("CENTER");
        components.add(titleComponent);
        
        // 2. Tableau si requis
        if (planSlide.getType().equals("content_with_table")) {
            DynamicComponent tableComponent = new DynamicComponent();
            tableComponent.setId("dynamic_table");
            tableComponent.setType("table");
            tableComponent.setPosition(new Position(0.8, 1.8, 8.4, 3.5));
            
            TableConfig tableConfig = new TableConfig();
            if (planSlide.getTableRequirements() != null) {
                tableConfig.setColumns(planSlide.getTableRequirements().getColumns());
                tableConfig.setHasHeader(planSlide.getTableRequirements().isHasHeader());
                tableConfig.setRowCount(planSlide.getTableRequirements().getRowCount());
            } else {
                tableConfig.setColumns(List.of("Colonne 1", "Colonne 2", "Colonne 3"));
                tableConfig.setHasHeader(true);
                tableConfig.setRowCount(5);
            }
            tableComponent.setTableConfig(tableConfig);
            components.add(tableComponent);
        }
        
        // 3. Graphique si requis
        if (planSlide.getType().equals("content_with_chart")) {
            DynamicComponent chartComponent = new DynamicComponent();
            chartComponent.setId("dynamic_chart");
            chartComponent.setType("chart");
            chartComponent.setPosition(new Position(1.0, 1.5, 8.0, 4.0));
            
            ChartConfig chartConfig = new ChartConfig();
            if (planSlide.getChartRequirements() != null) {
                chartConfig.setType(planSlide.getChartRequirements().getType());
                chartConfig.setTitle(planSlide.getChartRequirements().getTitle());
                chartConfig.setxAxis(planSlide.getChartRequirements().getxAxis());
                chartConfig.setyAxis(planSlide.getChartRequirements().getyAxis());
            } else {
                chartConfig.setType("bar");
                chartConfig.setTitle("Graphique");
                chartConfig.setxAxis("Catégories");
                chartConfig.setyAxis("Valeurs");
            }
            chartComponent.setChartConfig(chartConfig);
            components.add(chartComponent);
        }
        
        return components;
    }
}
```

---

## 6. MILESTONE 4 : GÉNÉRATEUR IA DE CONTENU

### Objectif

Générer le contenu réel pour chaque slide, avec des données concrètes.

### SlideContent (sortie pour un slide)

```json
{
  "slideId": "slide_6",
  "title": "Évolution historique de la population mondiale",
  "components": [
    {
      "type": "text",
      "zoneId": "zone_title",
      "content": "Évolution historique de la population mondiale"
    },
    {
      "type": "chart",
      "zoneId": "zone_chart",
      "position": {"x": 1.0, "y": 1.5, "w": 8.0, "h": 4.0},
      "data": {
        "chartType": "line",
        "title": "Population mondiale (en milliards)",
        "categories": ["1950", "1960", "1970", "1980", "1990", "2000", "2010", "2020"],
        "series": [
          {
            "name": "Population",
            "values": [2.5, 3.0, 3.7, 4.4, 5.3, 6.1, 6.9, 7.8]
          }
        ],
        "colors": ["#42B3BD"]
      },
      "source": "Source : Nations Unies, 2023"
    },
    {
      "type": "text",
      "zoneId": "zone_footer",
      "content": "Source : Nations Unies, 2023"
    }
  ]
}
```

### Implémentation du générateur

```java
public class AIContentGenerator {
    
    private final AIProvider aiProvider;
    private final ObjectMapper mapper;
    private static final Logger logger = LoggerFactory.getLogger(AIContentGenerator.class);
    
    public AIContentGenerator(AIProvider aiProvider) {
        this.aiProvider = aiProvider;
        this.mapper = new ObjectMapper();
    }
    
    public Map<String, SlideContent> generateContent(EnrichedPlan enrichedPlan, ContextInfo context) throws Exception {
        logger.info("Génération du contenu pour {} slides", enrichedPlan.getSlides().size());
        
        Map<String, SlideContent> contentMap = new LinkedHashMap<>();
        
        for (EnrichedSlide enrichedSlide : enrichedPlan.getSlides()) {
            SlideContent content = generateContentForSlide(enrichedSlide, context);
            contentMap.put(content.getSlideId(), content);
            logger.debug("Contenu généré pour slide: {}", content.getSlideId());
        }
        
        return contentMap;
    }
    
    private SlideContent generateContentForSlide(EnrichedSlide enrichedSlide, ContextInfo context) throws Exception {
        PlanSlide planSlide = enrichedSlide.getPlanSlide();
        
        // Construire le prompt
        String prompt = buildPrompt(enrichedSlide, context);
        
        // Appeler l'IA
        String systemPrompt = getSystemPrompt();
        String response = aiProvider.callAI(systemPrompt, prompt);
        
        // Parser la réponse
        SlideContent content = parseResponse(response);
        content.setSlideId(planSlide.getId());
        
        return content;
    }
    
    private String getSystemPrompt() {
        return """
            Tu es un expert en création de contenu pour présentations professionnelles.
            Tu génères des données précises, pertinentes et vérifiables.
            Pour les graphiques, utilise des données réalistes.
            Pour les tableaux, assure-toi que les données sont cohérentes.
            Les textes doivent être concis mais informatifs.
            Cite toujours les sources quand c'est pertinent.
            """;
    }
    
    private String buildPrompt(EnrichedSlide enrichedSlide, ContextInfo context) {
        PlanSlide planSlide = enrichedSlide.getPlanSlide();
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Génère le contenu pour ce slide de présentation.\n\n");
        
        prompt.append("📌 INFORMATIONS GÉNÉRALES :\n");
        prompt.append("   - Sujet global : ").append(context.getTopic()).append("\n");
        prompt.append("   - Public : ").append(context.getAudience()).append("\n");
        prompt.append("   - Ton : ").append(context.getTone()).append("\n\n");
        
        prompt.append("📋 SLIDE :\n");
        prompt.append("   - ID : ").append(planSlide.getId()).append("\n");
        prompt.append("   - Type : ").append(planSlide.getType()).append("\n");
        prompt.append("   - Titre attendu : ").append(planSlide.getTitle()).append("\n");
        if (planSlide.getSubtitle() != null) {
            prompt.append("   - Sous-titre attendu : ").append(planSlide.getSubtitle()).append("\n");
        }
        prompt.append("\n");
        
        prompt.append("📐 LAYOUT ASSIGNÉ :\n");
        if (!enrichedSlide.getLayout().isDynamic()) {
            prompt.append("   - ID : ").append(enrichedSlide.getLayout().getId()).append("\n");
            prompt.append("   - Type : ").append(enrichedSlide.getLayout().getSemanticType()).append("\n");
        } else {
            prompt.append("   - DYNAMIQUE : Créé à la volée\n");
        }
        prompt.append("\n");
        
        prompt.append("📝 ZONES À REMPLIR :\n");
        for (ZoneToFill zone : enrichedSlide.getZonesToFill()) {
            prompt.append("   - ").append(zone.getZoneId())
                 .append(" (type: ").append(zone.getType())
                 .append(") → ").append(zone.getExpectedContent()).append("\n");
        }
        prompt.append("\n");
        
        if (planSlide.getChartRequirements() != null) {
            prompt.append("📊 EXIGENCES POUR LE GRAPHIQUE :\n");
            ChartRequirements chartReq = planSlide.getChartRequirements();
            prompt.append("   - Type : ").append(chartReq.getType()).append("\n");
            prompt.append("   - Titre : ").append(chartReq.getTitle()).append("\n");
            prompt.append("   - Axe X : ").append(chartReq.getXAxis()).append("\n");
            prompt.append("   - Axe Y : ").append(chartReq.getYAxis()).append("\n");
            if (chartReq.getExpectedCategories() != null && !chartReq.getExpectedCategories().isEmpty()) {
                prompt.append("   - Catégories attendues : ").append(chartReq.getExpectedCategories()).append("\n");
            }
            prompt.append("\n");
        }
        
        if (planSlide.getTableRequirements() != null) {
            prompt.append("📋 EXIGENCES POUR LE TABLEAU :\n");
            TableRequirements tableReq = planSlide.getTableRequirements();
            prompt.append("   - Colonnes : ").append(tableReq.getColumns()).append("\n");
            prompt.append("   - En-tête : ").append(tableReq.isHasHeader() ? "Oui" : "Non").append("\n");
            prompt.append("   - Nombre de lignes : ").append(tableReq.getRowCount()).append("\n");
            prompt.append("\n");
        }
        
        if (planSlide.getItems() != null && !planSlide.getItems().isEmpty()) {
            prompt.append("📌 POINTS À INCLURE :\n");
            for (String item : planSlide.getItems()) {
                prompt.append("   - ").append(item).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("""
            🔴 FORMAT DE RÉPONSE (JSON STRICT) :
            {
              "slideId": "ID_DU_SLIDE",
              "title": "Titre final du slide",
              "components": [
                {
                  "type": "text|chart|table",
                  "zoneId": "zone_identifiant",
                  "content": "Texte" (pour type text),
                  "position": {"x": 0.0, "y": 0.0, "w": 0.0, "h": 0.0} (pour les composants dynamiques),
                  "data": { ... } (pour chart ou table),
                  "source": "Source des données" (optionnel)
                }
              ]
            }
            
            Pour un graphique (type: chart) :
            "data": {
              "chartType": "bar|line|pie",
              "title": "Titre du graphique",
              "categories": ["Cat1", "Cat2", ...],
              "series": [
                {"name": "Série 1", "values": [10, 20, ...]}
              ],
              "colors": ["#HEX", ...]
            }
            
            Pour un tableau (type: table) :
            "data": {
              "headers": ["Col1", "Col2", ...],
              "rows": [
                ["Val1", "Val2", ...],
                ...
              ]
            }
            """);
        
        prompt.append("\n🔴 IMPORTANT : Données réalistes, cohérentes et vérifiables. Réponds UNIQUEMENT avec le JSON.");
        
        return prompt.toString();
    }
    
    private SlideContent parseResponse(String response) throws Exception {
        // Nettoyer la réponse
        String cleanResponse = response.trim();
        if (cleanResponse.startsWith("```json")) {
            cleanResponse = cleanResponse.substring(7);
        }
        if (cleanResponse.startsWith("```")) {
            cleanResponse = cleanResponse.substring(3);
        }
        if (cleanResponse.endsWith("```")) {
            cleanResponse = cleanResponse.substring(0, cleanResponse.length() - 3);
        }
        cleanResponse = cleanResponse.trim();
        
        return mapper.readValue(cleanResponse, SlideContent.class);
    }
}
```

---

## 7. MILESTONE 5 : RENDERER PPTX

### Objectif

Générer le fichier PPTX final à partir des données enrichies.

### Implémentation du renderer

```java
public class PresentationRenderer {
    
    private static final double EMU_PER_INCH = 914400.0;
    private static final Logger logger = LoggerFactory.getLogger(PresentationRenderer.class);
    
    public void render(EnrichedPlan enrichedPlan, 
                       Map<String, SlideContent> contents,
                       String templatePath,
                       String outputPath) throws Exception {
        
        logger.info("Rendu de la présentation: {} slides", enrichedPlan.getSlides().size());
        
        // 1. Charger le template
        PresentationMLPackage presentation = PresentationMLPackage.load(new File(templatePath));
        
        // 2. Rendre chaque slide
        for (EnrichedSlide enrichedSlide : enrichedPlan.getSlides()) {
            SlideContent content = contents.get(enrichedSlide.getPlanSlide().getId());
            
            if (enrichedSlide.getRenderStrategy() == RenderStrategy.USE_TEMPLATE_LAYOUT) {
                renderWithTemplateLayout(presentation, enrichedSlide, content);
            } else {
                renderDynamicSlide(presentation, enrichedSlide, content);
            }
        }
        
        // 3. Sauvegarder
        presentation.save(new File(outputPath));
        logger.info("Présentation sauvegardée: {}", outputPath);
    }
    
    /**
     * Rendu avec un layout existant
     */
    private void renderWithTemplateLayout(PresentationMLPackage presentation,
                                          EnrichedSlide enrichedSlide,
                                          SlideContent content) throws Exception {
        
        // Récupérer la slide correspondante
        int slideIndex = enrichedSlide.getLayout().getSlideIndex();
        SlidePart slidePart = getSlideByIndex(presentation, slideIndex);
        
        // Pour chaque zone, remplir avec le contenu
        for (Map.Entry<String, String> mapping : enrichedSlide.getZoneMapping().entrySet()) {
            String zoneKey = mapping.getKey();
            String dataKey = mapping.getValue();
            
            // Trouver le composant correspondant dans le contenu
            SlideComponent component = findComponentByZone(content, zoneKey);
            if (component == null) {
                logger.warn("Aucun contenu trouvé pour la zone: {}", zoneKey);
                continue;
            }
            
            // Remplir la zone
            fillZone(slidePart, zoneKey, component);
        }
    }
    
    /**
     * Rendu d'une slide dynamique (créée à partir de zéro)
     */
    private void renderDynamicSlide(PresentationMLPackage presentation,
                                    EnrichedSlide enrichedSlide,
                                    SlideContent content) throws Exception {
        
        // 1. Créer une nouvelle slide
        SlidePart newSlidePart = presentation.createSlidePart();
        
        // 2. Ajouter les composants
        for (SlideComponent component : content.getComponents()) {
            switch (component.getType()) {
                case "text":
                    createTextFromScratch(newSlidePart, component);
                    break;
                case "chart":
                    createChartFromScratch(presentation, newSlidePart, component);
                    break;
                case "table":
                    createTableFromScratch(newSlidePart, component);
                    break;
            }
        }
        
        // 3. Ajouter le numéro de page
        addPageNumber(newSlidePart, enrichedSlide.getSlideIndex() + 1);
    }
    
    /**
     * Remplit une zone existante avec du texte
     */
    private void fillZone(SlidePart slidePart, String zoneKey, SlideComponent component) {
        // Identifier la shape à remplir par sa position ou son placeholder
        Shape targetShape = findShapeByZoneKey(slidePart, zoneKey);
        
        if (targetShape == null) {
            logger.warn("Shape non trouvée pour la zone: {}", zoneKey);
            return;
        }
        
        if (component.getType().equals("text")) {
            // Remplacer le texte
            setShapeText(targetShape, component.getContent());
        }
    }
    
    /**
     * Crée un texte à partir de zéro
     */
    private void createTextFromScratch(SlidePart slidePart, SlideComponent component) throws Exception {
        Shape shape = slidePart.createShapeOfType(ShapeType.RECTANGLE);
        
        // Position
        Position pos = component.getPosition();
        if (pos != null) {
            shape.setX(convertToEmu(pos.getX()));
            shape.setY(convertToEmu(pos.getY()));
            shape.setW(convertToEmu(pos.getW()));
            shape.setH(convertToEmu(pos.getH()));
        }
        
        // Texte
        shape.setText(component.getContent());
        
        // Style
        if (component.getStyle() != null) {
            ZoneStyle style = component.getStyle();
            if (style.getFontFamily() != null) {
                shape.setFontFamily(style.getFontFamily());
            }
            if (style.getFontSizePt() > 0) {
                shape.setFontSize(style.getFontSizePt());
            }
            if (style.getColor() != null) {
                shape.setFontColor(style.getColor());
            }
            if (style.getAlignment() != null) {
                shape.setAlign(style.getAlignment());
            }
        }
        
        slidePart.addShape(shape);
    }
    
    /**
     * Crée un graphique à partir de zéro
     */
    private void createChartFromScratch(PresentationMLPackage presentation,
                                        SlidePart slidePart,
                                        SlideComponent component) throws Exception {
        
        ChartData data = component.getData().getChartData();
        
        // 1. Créer le ChartPart
        ChartPart chartPart = ChartPart.createChartPart();
        
        // 2. Construire le CTChart
        CTChart chart = chartPart.getJaxbElement();
        
        // 3. Titre
        if (data.getTitle() != null) {
            CTTitle title = chart.addNewChartSpace().addNewChart().addNewTitle();
            CTTextBody titleBody = title.addNewTx().addNewRich();
            titleBody.addNewP().addNewR().setT(data.getTitle());
        }
        
        // 4. PlotArea
        CTPlotArea plotArea = chart.getChartSpace().getChart().addNewPlotArea();
        
        // 5. Type de graphique
        if (data.getChartType().equals("bar")) {
            createBarChart(plotArea, data);
        } else if (data.getChartType().equals("line")) {
            createLineChart(plotArea, data);
        } else if (data.getChartType().equals("pie")) {
            createPieChart(plotArea, data);
        }
        
        // 6. Sauvegarder le chart part
        chartPart.save();
        
        // 7. Créer le GraphicFrame
        GraphicFrame chartFrame = slidePart.addGraphicFrame();
        Position pos = component.getPosition();
        if (pos != null) {
            chartFrame.setX(convertToEmu(pos.getX()));
            chartFrame.setY(convertToEmu(pos.getY()));
            chartFrame.setW(convertToEmu(pos.getW()));
            chartFrame.setH(convertToEmu(pos.getH()));
        }
        
        // 8. Lier le chart part
        chartFrame.getGraphic().getGraphicData().setUri(
            "http://schemas.openxmlformats.org/drawingml/2006/chart"
        );
        chartFrame.getGraphic().getGraphicData().setId(chartPart.getId());
        
        slidePart.addGraphicFrame(chartFrame);
    }
    
    /**
     * Crée un graphique à barres
     */
    private void createBarChart(CTPlotArea plotArea, ChartData data) {
        CTBarChart barChart = plotArea.addNewBarChart();
        barChart.addNewVaryColors().setVal(false);
        barChart.addNewBarDir().setVal(STBarDir.COL);
        
        // Séries
        for (ChartSeries series : data.getSeries()) {
            CTBarSer barSer = barChart.addNewSer();
            
            // Nom
            barSer.addNewTx().addNewStrRef().addNewStrCache().addNewPt().setV(series.getName());
            
            // Valeurs
            CTNumRef numRef = barSer.addNewVal().addNewNumRef();
            numRef.addNewNumCache();
            for (int i = 0; i < series.getValues().size(); i++) {
                CTNumVal numVal = CTNumVal.Factory.newInstance();
                numVal.setIdx(i);
                numVal.setV(String.valueOf(series.getValues().get(i)));
                numRef.getNumCache().addPt(numVal);
            }
        }
        
        // Catégories
        CTAxDataSource catAx = barChart.addNewCat();
        CTStrRef strRef = catAx.addNewStrRef();
        strRef.addNewStrCache();
        for (int i = 0; i < data.getCategories().size(); i++) {
            CTStrVal strVal = CTStrVal.Factory.newInstance();
            strVal.setIdx(i);
            strVal.setV(data.getCategories().get(i));
            strRef.getStrCache().addPt(strVal);
        }
        
        // Ajouter les axes
        CTChartSpace cs = barChart.getChartSpace();
        if (cs == null) {
            // Configuration minimale des axes
        }
    }
    
    /**
     * Crée un tableau à partir de zéro
     */
    private void createTableFromScratch(SlidePart slidePart, SlideComponent component) throws Exception {
        TableData data = component.getData().getTableData();
        
        // 1. Créer CTTable
        CTTable table = CTTable.Factory.newInstance();
        
        // 2. Configurer la grille (colonnes)
        CTTblGrid tblGrid = table.addNewTblGrid();
        for (int i = 0; i < data.getHeaders().size(); i++) {
            tblGrid.addNewGridCol();
        }
        
        // 3. Ligne d'en-tête
        CTRow headerRow = table.addNewTr();
        headerRow.setH(convertToEmu(0.4));
        for (String header : data.getHeaders()) {
            CTTc cell = headerRow.addNewTc();
            CTTextBody textBody = cell.addNewTxBody();
            textBody.addNewP().addNewR().setT(header);
            // Style : fond gris
            cell.getTcPr().addNewSolidFill().addNewSrgbClr().setVal("E8E8E8");
            cell.getTcPr().addNewLnB().addNewNoFill();
        }
        
        // 4. Lignes de données
        for (List<String> rowData : data.getRows()) {
            CTRow row = table.addNewTr();
            row.setH(convertToEmu(0.35));
            for (String cellValue : rowData) {
                CTTc cell = row.addNewTc();
                CTTextBody textBody = cell.addNewTxBody();
                textBody.addNewP().addNewR().setT(cellValue);
                cell.getTcPr().addNewLnB().addNewNoFill();
            }
        }
        
        // 5. Créer le GraphicFrame
        GraphicFrame tableFrame = slidePart.addGraphicFrame();
        Position pos = component.getPosition();
        if (pos != null) {
            tableFrame.setX(convertToEmu(pos.getX()));
            tableFrame.setY(convertToEmu(pos.getY()));
            tableFrame.setW(convertToEmu(pos.getW()));
            tableFrame.setH(convertToEmu(pos.getH()));
        }
        
        // 6. Lier le tableau
        tableFrame.getGraphic().getGraphicData().setUri(
            "http://schemas.openxmlformats.org/drawingml/2006/table"
        );
        tableFrame.getGraphic().getGraphicData().setTbl(table);
        
        slidePart.addGraphicFrame(tableFrame);
    }
    
    /**
     * Ajoute un numéro de page
     */
    private void addPageNumber(SlidePart slidePart, int pageNumber) {
        // Créer une petite shape pour le numéro
        Shape numberShape = slidePart.createShapeOfType(ShapeType.OVAL);
        numberShape.setX(convertToEmu(9.3));
        numberShape.setY(convertToEmu(5.1));
        numberShape.setW(convertToEmu(0.4));
        numberShape.setH(convertToEmu(0.4));
        numberShape.setFillColor("42B3BD");
        numberShape.setText(String.valueOf(pageNumber));
        numberShape.setFontColor("FFFFFF");
        numberShape.setFontSize(12);
        numberShape.setBold(true);
        numberShape.setAlign("CENTER");
        numberShape.setValign("MIDDLE");
        
        slidePart.addShape(numberShape);
    }
    
    /**
     * Méthodes utilitaires
     */
    private long convertToEmu(double inches) {
        return Math.round(inches * EMU_PER_INCH);
    }
    
    private SlidePart getSlideByIndex(PresentationMLPackage presentation, int index) {
        List<SlidePart> slides = presentation.getParts().getPartsByType(SlidePart.class);
        if (index >= 0 && index < slides.size()) {
            return slides.get(index);
        }
        return null;
    }
}
```

---

## 8. MILESTONE 6 : INTÉGRATION ET ORCHESTRATION

### Point d'entrée principal

```java
public class PresentationGenerator {
    
    private final TemplateAnalyzer analyzer;
    private final PresentationPlanner planner;
    private final LayoutAssigner assigner;
    private final AIContentGenerator contentGenerator;
    private final PresentationRenderer renderer;
    private final ObjectMapper mapper;
    private static final Logger logger = LoggerFactory.getLogger(PresentationGenerator.class);
    
    public PresentationGenerator(AIProvider aiProvider) {
        this.analyzer = new TemplateAnalyzer();
        this.planner = new PresentationPlanner(aiProvider);
        this.assigner = new LayoutAssigner();
        this.contentGenerator = new AIContentGenerator(aiProvider);
        this.renderer = new PresentationRenderer();
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    /**
     * Méthode principale de génération
     */
    public GenerationResult generate(GenerationRequest request) throws Exception {
        logger.info("🚀 Début de la génération de présentation");
        long startTime = System.currentTimeMillis();
        
        GenerationResult result = new GenerationResult();
        
        // 1. ANALYSE DU TEMPLATE
        logger.info("📊 Étape 1 : Analyse du template");
        long stepStart = System.currentTimeMillis();
        TemplateStructure template = analyzer.analyze(request.getTemplatePath());
        result.setTemplateAnalysisTime(System.currentTimeMillis() - stepStart);
        result.setLayoutCount(template.getLayouts().size());
        logger.info("   - {} layouts analysés en {}ms", 
                   template.getLayouts().size(), result.getTemplateAnalysisTime());
        
        // 2. PLANIFICATION
        logger.info("📋 Étape 2 : Planification de la présentation");
        stepStart = System.currentTimeMillis();
        PlanRequest planRequest = new PlanRequest();
        planRequest.setTopic(request.getTopic());
        planRequest.setAudience(request.getAudience());
        planRequest.setTone(request.getTone());
        planRequest.setSlideCount(request.getSlideCount());
        planRequest.setConstraints(request.getConstraints());
        planRequest.setAvailableLayouts(getAvailableLayoutTypes(template));
        
        PresentationPlan plan = planner.generatePlan(planRequest);
        result.setPlanningTime(System.currentTimeMillis() - stepStart);
        result.setSlideCount(plan.getSlides().size());
        logger.info("   - {} slides planifiées en {}ms", 
                   plan.getSlides().size(), result.getPlanningTime());
        
        // 3. ASSIGNATION DES LAYOUTS
        logger.info("📐 Étape 3 : Assignation des layouts");
        stepStart = System.currentTimeMillis();
        EnrichedPlan enrichedPlan = assigner.assignLayouts(template, plan);
        result.setAssignmentTime(System.currentTimeMillis() - stepStart);
        long dynamicCount = enrichedPlan.getSlides().stream()
            .filter(s -> s.getLayout().isDynamic()).count();
        result.setDynamicSlideCount((int) dynamicCount);
        logger.info("   - {} layouts assignés, {} dynamiques en {}ms",
                   enrichedPlan.getSlides().size() - dynamicCount, 
                   dynamicCount, result.getAssignmentTime());
        
        // 4. GÉNÉRATION DU CONTENU
        logger.info("📝 Étape 4 : Génération du contenu par IA");
        stepStart = System.currentTimeMillis();
        ContextInfo context = new ContextInfo();
        context.setTopic(request.getTopic());
        context.setAudience(request.getAudience());
        context.setTone(request.getTone());
        
        Map<String, SlideContent> contents = contentGenerator.generateContent(enrichedPlan, context);
        result.setContentGenerationTime(System.currentTimeMillis() - stepStart);
        logger.info("   - Contenu généré pour {} slides en {}ms",
                   contents.size(), result.getContentGenerationTime());
        
        // 5. RENDU PPTX
        logger.info("🎨 Étape 5 : Rendu PPTX");
        stepStart = System.currentTimeMillis();
        String outputPath = request.getOutputPath();
        renderer.render(enrichedPlan, contents, request.getTemplatePath(), outputPath);
        result.setRenderingTime(System.currentTimeMillis() - stepStart);
        result.setOutputPath(outputPath);
        logger.info("   - PPTX généré en {}ms", result.getRenderingTime());
        
        // 6. RÉSUMÉ
        result.setTotalTime(System.currentTimeMillis() - startTime);
        logger.info("✅ Génération terminée en {}ms", result.getTotalTime());
        logger.info("   📊 Résumé : {} slides, {} layouts, {} dynamiques, {}ms total",
                   result.getSlideCount(),
                   result.getLayoutCount(),
                   result.getDynamicSlideCount(),
                   result.getTotalTime());
        
        return result;
    }
    
    private List<String> getAvailableLayoutTypes(TemplateStructure template) {
        return template.getLayouts().stream()
            .map(SlideLayout::getSemanticType)
            .distinct()
            .collect(Collectors.toList());
    }
}

// GenerationRequest.java
public class GenerationRequest {
    private String templatePath;
    private String outputPath;
    private String topic;
    private String audience;
    private String tone;
    private int slideCount;
    private List<String> constraints;
    // getters/setters
}

// GenerationResult.java
public class GenerationResult {
    private String outputPath;
    private int slideCount;
    private int layoutCount;
    private int dynamicSlideCount;
    private long templateAnalysisTime;
    private long planningTime;
    private long assignmentTime;
    private long contentGenerationTime;
    private long renderingTime;
    private long totalTime;
    // getters/setters
}

// ContextInfo.java
public class ContextInfo {
    private String topic;
    private String audience;
    private String tone;
    // getters/setters
}
```

---

## 9. EXEMPLE D'UTILISATION COMPLET

### Code client

```java
public class Main {
    
    public static void main(String[] args) throws Exception {
        // 1. Configuration
        String apiKey = System.getenv("OPENAI_API_KEY");
        AIProvider aiProvider = new AIProvider(apiKey, "gpt-4o-mini");
        
        // 2. Création du générateur
        PresentationGenerator generator = new PresentationGenerator(aiProvider);
        
        // 3. Construction de la requête
        GenerationRequest request = new GenerationRequest();
        request.setTemplatePath("src/main/resources/templates/template.pptx");
        request.setOutputPath("generated/presentation_2025.pptx");
        request.setTopic("Évolution de la population mondiale");
        request.setAudience("Étudiants en démographie");
        request.setTone("professionnel et éducatif");
        request.setSlideCount(8);
        request.setConstraints(List.of(
            "Inclure au moins un graphique",
            "Inclure un tableau de données",
            "Max 5 points par slide"
        ));
        
        // 4. Génération
        GenerationResult result = generator.generate(request);
        
        // 5. Affichage du résumé
        System.out.println("\n📊 RÉSULTAT DE LA GÉNÉRATION");
        System.out.println("================================");
        System.out.println("📁 Fichier généré : " + result.getOutputPath());
        System.out.println("📄 Nombre de slides : " + result.getSlideCount());
        System.out.println("📐 Layouts disponibles : " + result.getLayoutCount());
        System.out.println("🔧 Slides dynamiques : " + result.getDynamicSlideCount());
        System.out.println("⏱️  Temps total : " + result.getTotalTime() + "ms");
        System.out.println("\nDétail des temps :");
        System.out.println("   - Analyse template : " + result.getTemplateAnalysisTime() + "ms");
        System.out.println("   - Planification : " + result.getPlanningTime() + "ms");
        System.out.println("   - Assignation : " + result.getAssignmentTime() + "ms");
        System.out.println("   - Génération IA : " + result.getContentGenerationTime() + "ms");
        System.out.println("   - Rendu : " + result.getRenderingTime() + "ms");
    }
}
```

### Exemple de sortie console

```
🚀 Début de la génération de présentation
📊 Étape 1 : Analyse du template
   - 13 layouts analysés en 234ms
📋 Étape 2 : Planification de la présentation
   - 8 slides planifiées en 1250ms
📐 Étape 3 : Assignation des layouts
   - 6 layouts assignés, 2 dynamiques en 45ms
📝 Étape 4 : Génération du contenu par IA
   - Contenu généré pour 8 slides en 8450ms
🎨 Étape 5 : Rendu PPTX
   - PPTX généré en 890ms
✅ Génération terminée en 10869ms
   📊 Résumé : 8 slides, 13 layouts, 2 dynamiques, 10869ms total

📊 RÉSULTAT DE LA GÉNÉRATION
================================
📁 Fichier généré : generated/presentation_2025.pptx
📄 Nombre de slides : 8
📐 Layouts disponibles : 13
🔧 Slides dynamiques : 2
⏱️  Temps total : 10869ms

Détail des temps :
   - Analyse template : 234ms
   - Planification : 1250ms
   - Assignation : 45ms
   - Génération IA : 8450ms
   - Rendu : 890ms
```

---

## 10. PLANNING ET LIVRABLES

### Planning indicatif

| Milestone | Durée | Livrables |
| :--- | :--- | :--- |
| **M1 - Analyseur** | 3 jours | TemplateAnalyzer, modèles, tests, JSON structure |
| **M2 - Planificateur** | 2 jours | PresentationPlanner, AIProvider, prompts, tests |
| **M3 - Layout Assigner** | 2 jours | LayoutAssigner, matching algorithm, tests |
| **M4 - Générateur IA** | 3 jours | AIContentGenerator, prompts optimisés, tests |
| **M5 - Renderer** | 4 jours | PresentationRenderer, composants dynamiques, tests |
| **M6 - Intégration** | 2 jours | PresentationGenerator, orchestration, tests E2E |
| **Total** | **16 jours** | **Librairie complète + tests + documentation** |

### Structure des livrables

```
livrables/
├── docs/
│   ├── API.md                    # Documentation de l'API
│   ├── architecture.md           # Architecture détaillée
│   └── user-guide.md             # Guide utilisateur
├── src/
│   ├── main/java/.../            # Code source
│   ├── test/java/.../            # Tests unitaires
│   └── resources/
│       └── templates/            # Templates d'exemple
├── generated/                    # Présentations générées
├── pom.xml                       # Dépendances Maven
└── README.md                     # Présentation du projet
```

### Checklist de validation

- [ ] L'analyseur extrait correctement les 13 layouts
- [ ] Les styles (polices, couleurs) sont préservés
- [ ] Le planificateur génère un plan cohérent
- [ ] Le layout assigner match correctement les types
- [ ] L'IA génère des données réalistes
- [ ] Le renderer produit un PPTX fidèle au template
- [ ] Les slides dynamiques sont créées correctement
- [ ] Les tableaux et graphiques sont bien rendus
- [ ] La présentation finale est ouverte sans erreur

---

Cette spécification est maintenant complète et prête à être donnée à un agent IA de code. Elle couvre tous les aspects du projet, du début à la fin, avec des prompts, des structures de données, des implémentations et des exemples d'utilisation.