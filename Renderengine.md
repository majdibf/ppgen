
Actuellement (usine à gaz)

```java
// Trop complexe, trop de détails
SlideLayoutPart layoutPart = findLayoutPart(...);
PartName slidePartName = new PartName("/ppt/slides/slide" + index + ".xml");
SlidePart slidePart = new SlidePart(slidePartName);
pptx.getParts().put(slidePart);
slidePart.addTargetPart(layoutPart);
inheritLayoutPlaceholders(layoutPart, slidePart);
fillInheritedPlaceholders(slidePart, content);
mainPart.addSlide(slidePart);
```

Ce que tu veux (moteur simple)

```java
// Idéal : 1 ligne
pptxEngine.createSlideFromLayout(layoutId, content);
```

---

Architecture du moteur de rendu

```
┌─────────────────────────────────────────────────────────────────┐
│                    PptxRenderEngine                            │
│  (API publique simple)                                         │
├─────────────────────────────────────────────────────────────────┤
│  createSlideFromLayout(layoutId, content) → SlidePart          │
│  createPresentation(templatePath, contentMap) → File           │
│  renderSlide(slideData) → SlidePart                            │
│  injectContent(slidePart, content) → void                      │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Internal Helpers                             │
│  (cache la complexité docx4j)                                  │
├─────────────────────────────────────────────────────────────────┤
│  LayoutResolver (avec cache)                                   │
│  PlaceholderCloner (gère la copie propre)                      │
│  ContentInjector (gère les bullets, styles, etc.)              │
│  SlideBuilder (crée les slides)                                │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    docx4j (bas niveau)                          │
└─────────────────────────────────────────────────────────────────┘
```

---

Code du moteur simplifié

1. API publique

```java
/**
 * Moteur de rendu PPTX - API simple et réutilisable
 */
@ApplicationScoped
public class PptxRenderEngine {
    
    private final LayoutResolver layoutResolver;
    private final PlaceholderCloner placeholderCloner;
    private final ContentInjector contentInjector;
    private final SlideBuilder slideBuilder;
    
    @Inject
    public PptxRenderEngine(LayoutResolver layoutResolver,
                            PlaceholderCloner placeholderCloner,
                            ContentInjector contentInjector,
                            SlideBuilder slideBuilder) {
        this.layoutResolver = layoutResolver;
        this.placeholderCloner = placeholderCloner;
        this.contentInjector = contentInjector;
        this.slideBuilder = slideBuilder;
    }
    
    /**
     * Crée une présentation complète à partir d'un template et d'un contenu.
     */
    public File createPresentation(String templatePath, List<SlideData> slides, String outputPath) {
        PresentationMLPackage pptx = loadTemplate(templatePath);
        MainPresentationPart mainPart = pptx.getMainPresentationPart();
        
        // Purge
        purgeExistingSlides(pptx, mainPart);
        
        // Pour chaque slide
        for (int i = 0; i < slides.size(); i++) {
            SlideData slideData = slides.get(i);
            SlidePart slidePart = createSlideFromLayout(pptx, mainPart, slideData, i);
            renderSlide(pptx, mainPart, slidePart, slideData);
        }
        
        pptx.save(new File(outputPath));
        return new File(outputPath);
    }
    
    /**
     * Crée une slide à partir d'un layout.
     */
    public SlidePart createSlideFromLayout(PresentationMLPackage pptx,
                                           MainPresentationPart mainPart,
                                           SlideData slideData,
                                           int index) {
        // 1. Résoudre le layout
        SlideLayoutPart layoutPart = layoutResolver.resolve(slideData.getLayoutId());
        
        // 2. Créer la slide
        SlidePart slidePart = slideBuilder.createSlide(pptx, mainPart, layoutPart, index);
        
        // 3. Cloner les placeholders
        placeholderCloner.clonePlaceholders(layoutPart, slidePart);
        
        // 4. Injecter le contenu
        contentInjector.inject(slidePart, slideData.getContent());
        
        return slidePart;
    }
}
```

---

2. LayoutResolver (avec cache)

```java
@ApplicationScoped
public class LayoutResolver {
    
    private final Map<String, SlideLayoutPart> cache = new HashMap<>();
    private PresentationMLPackage pptx;
    
    public void init(PresentationMLPackage pptx) {
        this.pptx = pptx;
        this.cache.clear();
    }
    
    public SlideLayoutPart resolve(String layoutId, String originalName) {
        return cache.computeIfAbsent(layoutId, id -> {
            for (Part part : pptx.getParts().getParts().values()) {
                if (part instanceof SlideLayoutPart layoutPart) {
                    if (layoutPart.getContents().getCSld().getName().equals(originalName)) {
                        return layoutPart;
                    }
                }
            }
            throw new PptxException("Layout not found: " + layoutId);
        });
    }
}
```

---

3. PlaceholderCloner (gère la copie propre)

```java
@ApplicationScoped
public class PlaceholderCloner {
    
    private int shapeIdCounter = 1000;
    
    public void clonePlaceholders(SlideLayoutPart layoutPart, SlidePart slidePart) {
        SldLayout source = layoutPart.getContents();
        Sld target = slidePart.getContents();
        
        if (source.getCSld() == null || source.getCSld().getSpTree() == null) return;
        if (target.getCSld() == null) target.setCSld(new CommonSlideData());
        if (target.getCSld().getSpTree() == null) target.getCSld().setSpTree(new GroupShape());
        
        List<Object> targetShapes = target.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame();
        
        for (Object value : source.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame()) {
            if (!(value instanceof Shape sourceShape)) continue;
            if (!isPlaceholder(sourceShape)) continue;
            
            Shape clonedShape = copyShape(sourceShape);
            targetShapes.add(clonedShape);
        }
    }
    
    private Shape copyShape(Shape sourceShape) {
        // ✅ Copie propre (sans deepCopy)
        Shape newShape = new Shape();
        
        // Copier NVSpPr
        // Copier SPPr (position, taille)
        // Copier placeholder type
        // Créer TxBody vide
        
        return newShape;
    }
    
    private boolean isPlaceholder(Shape shape) {
        return shape.getNvSpPr() != null 
            && shape.getNvSpPr().getNvPr() != null 
            && shape.getNvSpPr().getNvPr().getPh() != null;
    }
}
```

---

4. ContentInjector (gère les bullets, styles, etc.)

```java
@ApplicationScoped
public class ContentInjector {
    
    public void inject(SlidePart slidePart, Map<String, String> content) {
        Sld slide = slidePart.getContents();
        List<Object> shapes = slide.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame();
        
        for (Map.Entry<String, String> entry : content.entrySet()) {
            String zoneKey = entry.getKey();
            String text = entry.getValue();
            
            Shape targetShape = findShapeByZoneKey(shapes, zoneKey);
            if (targetShape == null) continue;
            
            injectText(targetShape, text);
        }
    }
    
    private void injectText(Shape shape, String text) {
        CTTextBody txBody = shape.getTxBody();
        if (txBody == null) {
            txBody = new CTTextBody();
            shape.setTxBody(txBody);
        }
        
        txBody.getP().clear();
        
        // Détection des bullets
        if (text.contains("\n")) {
            injectBullets(txBody, text.split("\n"));
        } else {
            injectSimpleText(txBody, text);
        }
    }
    
    private void injectSimpleText(CTTextBody txBody, String text) {
        CTTextParagraph p = new CTTextParagraph();
        CTRegularTextRun run = new CTRegularTextRun();
        run.setT(text);
        p.getEGTextRun().add(run);
        txBody.getP().add(p);
    }
    
    private void injectBullets(CTTextBody txBody, String[] lines) {
        for (String line : lines) {
            if (line.isBlank()) continue;
            CTTextParagraph p = new CTTextParagraph();
            CTRegularTextRun run = new CTRegularTextRun();
            run.setT(line.trim());
            p.getEGTextRun().add(run);
            txBody.getP().add(p);
        }
    }
}
```

---

5. SlideBuilder

```java
@ApplicationScoped
public class SlideBuilder {
    
    public SlidePart createSlide(PresentationMLPackage pptx,
                                 MainPresentationPart mainPart,
                                 SlideLayoutPart layoutPart,
                                 int index) throws Exception {
        PartName slidePartName = new PartName("/ppt/slides/slide" + (index + 1) + ".xml");
        SlidePart slidePart = new SlidePart(slidePartName);
        pptx.getParts().put(slidePart);
        slidePart.addTargetPart(layoutPart);
        mainPart.addSlide(slidePart);
        return slidePart;
    }
}
```

---

Utilisation du moteur

```java
// 1. Préparer les données
List<SlideData> slides = new ArrayList<>();
slides.add(new SlideData("layout_1", Map.of(
    "center_title_1", "Transformation Digitale 2024",
    "subtitle_2", "Stratégie et Roadmap"
)));

// 2. Générer la présentation
PptxRenderEngine engine = new PptxRenderEngine(...);
File result = engine.createPresentation("template.pptx", slides, "output.pptx");

// 3. C'est tout !
```

---

Résumé



Le but : faire du renderer une "boîte noire" que tout le monde peut utiliser sans comprendre docx4j.
