# Réflexion : comment je m'y prendrais pour refaire cet outil

> Document d'architecture alternative. Pas une critique de l'existant (cf.
> `docs/REVIEW.md` pour ça) — une vision « si je partais de zéro, comment je découpe ? ».
> Les références à du code existant servent uniquement à montrer où ça simplifie.

---

## 1. Vision générale

**Principe** : un template PPTX est un fichier OOXML zippé. On peut le lire, le modifier,
le réécrire, sans aucun LLM dans la boucle pour la *plomberie*. L'IA sert **uniquement** à :

1. Décider le **contenu** (quoi dire),
2. Décider l'**ordre des slides** et la **sélection du layout** (quelle page, quel layout).

Tout le reste — inspection des zones, mapping texte → forme, rendu — est du code déterministe.

```
┌─────────────┐    ┌──────────────┐    ┌──────────────┐
│  Template  │───▶│  Analyseur   │───▶│  Modèle de  │   ← pur OOXML, pas d'IA
│  .pptx     │    │  (déterministe)│   │  template   │
└─────────────┘    └──────────────┘    └──────┬───────┘
                                            │
                          ┌─────────────────┼─────────────────┐
                          ▼                 ▼                 ▼
                   ┌─────────────┐   ┌──────────────┐   ┌─────────────┐
                   │ Planner IA  │   │ Assigner     │   │ Generator   │
                   │ (quoi dire) │   │ (quel layout)│   │ IA (texte)  │
                   └──────┬──────┘   └──────┬───────┘   └──────┬──────┘
                          │                │                  │
                          └────────┬───────┴────────┬─────────┘
                                   ▼                ▼
                            ┌──────────────────────────┐
                            │ Renderer (déterministe) │
                            │ copie template, injecte  │
                            └──────────────────────────┘
                                   │
                                   ▼
                            ┌─────────────┐
                            │ output.pptx │
                            └─────────────┘
```

Trois boîtes bleues (analyseur / assigner / renderer) sont **pures**. Deux boîtes oranges
utilisent l'IA. C'est la séparation qui change tout : aujourd'hui, IA et code déterministe
sont entremêlés à l'intérieur de presque chaque étape.

---

## 2. Le modèle de template — la pièce centrale

Aujourd'hui on a `LayoutAnalysis { layoutId, originalName, semanticType, zones[] }` mais
`zones[]` mélange quatre choses : index de tri, type OOXML, géométrie, clé composite. Je
propose de **séparer** en trois couches :

```java
// 1. La forme brute, telle qu'elle existe dans le PPTX
record PlaceholderShape(
    int idx,              // p:ph/@idx si présent, sinon -1
    String type,          // p:ph/@type (title, body, pic, ...) ou null
    int zOrder,           // position dans le SpTree (utilisée pour les replis)
    Rect geometry,        // x, y, w, h en EMU
    boolean hasText       // a un <a:txBody> utilisable
) {}

// 2. Le rôle sémantique — stable, validé par l'IA une fois
record SemanticRole(
    String id,            // "title", "subtitle", "body", "picture", "single-fact", "quote"
    Set<String> acceptedTypes,  // p:ph/@type compatibles (title, body, ...)
    boolean required,
    Integer maxChars
) {}

// 3. Le binding entre les deux
record Zone(
    PlaceholderShape shape,
    SemanticRole role
) {}

record TemplateLayout(
    String id,            // "title-slide", "content-single", "section-break", ...
    String displayName,   // nom humain (pour debug)
    List<Zone> zones
) {}

record TemplateModel(
    Size slideSize,
    List<TemplateLayout> layouts,
    // Mapping déterministe: layoutId -> PartName interne du PPTX
    // C'est ça qui supprime la jointure par nom de chaîne
    Map<String, String> layoutPartNames
) {}
```

**Trois gains immédiats** :

1. La forme brute et le rôle sont **deux objets différents** : si on re-classe un rôle plus
   tard, on ne touche pas aux géométries.
2. `layoutPartNames` mappe `layoutId` → `PartName` interne (`/ppt/slideLayouts/slideLayout5.xml`)
   → plus de comparaison de chaîne muette comme dans `SlideFactory.resolveLayout`.
3. Les replis ne sont plus dans `PlaceholderMapper` (qui devient trivial) mais dans
   l'**assigner** — qui est la bonne couche, parce que l'assigner *décide* quel layout
   utiliser, il ne *découvre* pas.

---

## 3. L'analyseur (M1) — déterministe, jamais d'IA

```java
TemplateModel analyze(byte[] pptxBytes) {
    var pkg = PresentationMLPackage.load(pptxBytes);
    var size = readSlideSize(pkg);
    var layouts = pkg.getParts().values().stream()
        .filter(SlideLayoutPart.class::isInstance)
        .map(SlideLayoutPart.class::cast)
        .map(part -> buildLayout(part, size))
        .toList();
    return new TemplateModel(size, layouts, buildPartNameIndex(pkg, layouts));
}

TemplateLayout buildLayout(SlideLayoutPart part, Size size) {
    var shapes = readShapesInOrder(part);  // ordre = ordre XML
    var zones = shapes.stream()
        .filter(this::isUsablePlaceholder)  // a p:ph + xfrm
        .map(s -> new Zone(s, inferRole(s, size)))  // heuristique simple
        .toList();
    return new TemplateLayout(
        deriveLayoutId(part),  // depuis PartName, pas depuis displayName
        part.getPartName().getName()
    );
}
```

**Rôle localement inféré sans IA** : `title` si `type=title` ou position haute+grande,
`body` si position centrale+grande, etc. Si l'IA n'est pas dispo, **ça marche quand même**,
on perd juste la classification sémantique fine (TWO_COLUMN vs CONTENT_SINGLE — distinction
que seul un humain/LLM fait bien).

**L'IA de classification** (si on la garde) devient **un validateur asif** : « ce rôle me
paraît faux, en voici une meilleure suggestion ». Pas un reconstructeur de la donnée.

---

## 4. Le renderer (M5) — la grosse simplification

Aujourd'hui le rendu fait quatre choses : purge, création de slide, clonage de placeholders,
mapping zone → shape. C'est trop. Je le sépare en deux :

```java
class SlideCloner {
    // Pour un layout donné, crée une slide vide avec TOUS ses placeholders clonés
    SlidePart cloneLayoutInto(PresentationMLPackage pkg, TemplateLayout layout) {
        var slidePart = pkg.createSlide(layout.partName());
        layout.zones().forEach(z -> slidePart.appendShape(deepCopy(z.shape())));
        return slidePart;
    }
}

class TextInjector {
    // Étant donné une slide clonée, un layout, et une map roleId -> texte, injecte.
    // C'est ici, et ici seulement, qu'on a besoin d'une politique de matching.
    List<Warning> inject(SlidePart slide, TemplateLayout layout, Map<String, String> textByRole) {
        var warnings = new ArrayList<Warning>();
        for (var zone : layout.zones()) {
            var text = textByRole.get(zone.role().id());
            if (text == null) {
                if (zone.role().required()) warnings.add(missing(zone));
                continue;
            }
            var shape = slide.findShape(z -> sameShape(z, zone.shape()));  // match par idx puis zOrder
            if (shape == null) {
                warnings.add(unmappable(zone, text));
                continue;
            }
            writeText(shape, text, zone.role());
        }
        return warnings;
    }
}
```

**Trois différences avec l'existant** :

- Le **mapping est par `role.id`**, pas par `ZoneKey = type_id` composite. Plus de compteur
  fragile. Le rôle est stable ; si on ajoute une zone, on lui donne un nouveau rôle.
- Le **fallback est explicite** : si `role.required` et pas de texte → warning typé. Si
  shape introuvable → warning typé. Pas de `get(0)` muet.
- Le **rendu n'inspecte jamais la géométrie** pour choisir une shape. Il compare la
  `PlaceholderShape` clonée à la `PlaceholderShape` originale par idx puis zOrder — deux
  champs discrets, pas un calcul continu.

---

## 5. Le planner + assigner (M2 + M3) — là où l'IA est utile

Ces deux-là sont ceux qui *méritent* l'IA. La question à poser au LLM est simple :

> « Voici le modèle de template (rôles disponibles par layout), voici les inputs. Donne-moi
> un plan : pour chaque slide, quel layout utiliser et quel texte mettre dans chaque rôle. »

**Une seule passe IA** suffit :

```json
{
  "slides": [
    {
      "layoutId": "title-slide",
      "text": { "title": "...", "subtitle": "..." }
    },
    {
      "layoutId": "content-two-column",
      "text": { "title": "...", "body-left": "...", "body-right": "..." }
    }
  ]
}
```

Validations après l'appel (zéro IA) :

- Tous les `layoutId` existent dans le modèle.
- Toutes les clés `text` sont des `role.id` du layout choisi.
- Tous les rôles `required` ont du texte.

Le plan est directement injectable. **Plus de tri, plus de zip, plus de positionnel, plus de
ZoneKey composite.**

---

## 6. La sortie — un seul fichier de binding

Le fichier JSON du plan devient l'artefact central :

```json
{
  "templateModel": { ... },  // produit par l'analyseur
  "plan":         { ... },  // produit par le planner+assigner (1 appel IA)
  "render":       { ... }   // produit par le renderer (déterministe)
}
```

Si demain on change de template, on relance l'analyseur, on garde le plan, on re-render.
Si on change de contenu, on garde le modèle, on relance juste planner + renderer. Si on ne
change rien, le JSON est le cache de debug.

---

## 7. Les invariants qu'on gagne

| Invariant                         | Aujourd'hui                                    | Proposé                            |
|-----------------------------------|------------------------------------------------|------------------------------------|
| Identité d'une zone               | `zoneType_zoneId` (string concat)              | `role.id` (énuméré)                |
| Jointure layout → fichier         | par displayName (chaîne mutable)               | par `PartName` OOXML (stable)      |
| Matching shape au rendu           | cascade 4 étages + `get(0)` muet               | idx puis zOrder, sinon warning     |
| Matching content → zone           | tri par `zoneId` puis zip                      | map explicite `roleId → text`      |
| Enrichissement zones/layouts      | 2 appels IA séparés, muets en cas d'échec      | 0 appel IA pour les zones          |
| Taille du code renderer           | ~600 lignes (Mapper + Injector + Factory + Helper) | ~150 lignes (Cloner + Injector) |

---

## 8. Ce qui reste dur malgré tout

Honnêtement, quelques problèmes ne disparaissent pas avec cette architecture :

1. **Les zones non-placeholder** (texte libre dans un layout custom) : il faut décider de
   les ignorer, les indexer quand même, ou demander à l'IA de les étiqueter. Le pipeline
   actuel les ignore — la nouvelle archi peut faire pareil, c'est cohérent.

2. **Les layouts « presque-bon »** : un layout a une zone title + une zone body, mais le
   contenu de l'utilisateur a 3 points → il faut soit un layout avec 3 body, soit
   condenser. Ça reste un choix IA + fallback déterministe.

3. **Le format de puces / gras / couleur** : si l'utilisateur veut des bullets niveau 2,
   on doit savoir le *dire* dans le prompt et le *parser* dans l'injecteur. C'est le
   couplage prompt ↔ injector dont je parlais dans `docs/REVIEW.md` §3 (R8).

4. **Les images** : PICTURE / CHART / TABLE sont des zones non textuelles. Soit on laisse
   l'IA décider de les remplir via une URL, soit on les laisse vides. Aujourd'hui c'est la
   deuxième option — il faut la documenter.

---

## 9. Ce que je changerais *en premier* si je reprenais ce projet

Par ordre :

1. **Introduire `TemplateModel` avec `layoutPartNames` (Map layoutId → PartName).** C'est
   1 fichier nouveau, 1 méthode dans `TemplateAnalyzer`, 1 méthode dans `SlideFactory`. Ça
   élimine la première jointure muette et donne un point d'ancrage stable.

2. **Sortir le matching du renderer, le mettre dans l'assigner.** L'assigner choisit, le
   renderer applique. Ça veut dire : le renderer reçoit `Map<roleId, text>` (pas
   `Map<ZoneKey, text>`), et son code de matching disparaît.

3. **Remplacer `ZoneKey` par `role.id` dans toute la chaîne.** Une seule convention de clé,
   partout.

4. **Injecter `ObjectMapper` CDI, supprimer `processAsync`, privatiser les champs.** Zéro
   risque, code propre.

5. **Tests d'intégration sur la chaîne `TemplateModel → Plan → Render`** avec une fixture
   PPTX simple, qui produit un PPTX de référence. C'est la seule façon de prouver que tout
   marche bout-en-bout.

---

## 10. Questions ouvertes pour la discussion

- Est-ce que le coût d'introduire `TemplateModel` / `PlaceholderShape` / `SemanticRole`
  (3 nouveaux records + leur sérialisation Jackson) vaut le gain ? Ou on essaie d'abord un
  *quick win* sur le seul `layoutPartNames` ?

- Est-ce que la séparation `SlideCloner` / `TextInjector` est utile, ou est-ce qu'un seul
  `Renderer` qui fait les deux est plus simple pour un projet de cette taille ?

- L'enrichissement IA de M1 (`enrichZoneDescriptions` + `enrichLayoutsAndClassify`) sert
  principalement aux prompts M2/M3/M4. Si on supprime ces prompts et qu'on les remplace
  par un schéma JSON strict dérivé du `TemplateModel`, est-ce qu'on peut se passer
  complètement de l'enrichissement IA en M1 ?

- Le test E2E « un PPTX en entrée, un PPTX en sortie » est le seul qui prouve la chaîne.
  Combien de fixtures on garde ? Un seul `template_1.pptx` suffit-il, ou il en faut
  plusieurs pour couvrir les cas (title, two-column, content avec image) ?