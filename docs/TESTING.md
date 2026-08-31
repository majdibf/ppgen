# Guide de Tests — pptx_generator

> Comment écrire, classifier et maintenir des tests dans ce projet.
> Basé sur JUnit 5, Quarkus CDI, docx4j, et H2 (test).

---

## Règle universelle : les 3 niveaux de tests

Tout test se classe en **3 catégories**. La différence est simple :
**quel est le périmètre testé, et quelles dépendances sont réelles ?**

```
┌─────────────────────────────────────────────────────────┐
│  Test unitaire                                          │
│  • 1 seule classe                                       │
│  • Dépendances = mocks (pas de DB, pas de réseau)      │
│  • Rapide (< 1s)                                        │
│  • Objectif : tester la LOGIQUE métier                  │
├─────────────────────────────────────────────────────────┤
│  Test d'intégration                                     │
│  • 1 couche avec ses vraies dépendances                 │
│  • DB en mémoire (H2), vrai template PPTX, vrai docx4j  │
│  • Plus lent (1-10s)                                    │
│  • Objectif : tester que les COUCHES collaborent         │
├─────────────────────────────────────────────────────────┤
│  Test E2E (end-to-end)                                  │
│  • Pipeline complet M1→M5                               │
│  • Vrai template + IA mock + DB H2                      │
│  • Lent (10-30s)                                        │
│  • Objectif : tester le FLUX COMPLET de bout en bout    │
└─────────────────────────────────────────────────────────┘
```

---

## Test unitaire

### Définition

Un test unitaire vérifie **une seule classe**, isolément. On remplace ses dépendances
par des **mocks** (objets factices qui retournent des valeurs prédéfinies).

**Règle** : si tu changes le code d'une autre classe, le test ne doit PAS casser.

### Quand l'utiliser ?

- La classe a de la **logique métier** (calculs, décisions, transformations)
- La classe est **pure** (pas de side effects : pas de DB, pas de fichier, pas de réseau)
- Tu veux tester **plusieurs cas** (cas normal, cas limite, cas d'erreur)

### Exemples concrets du projet

| Classe | Ce qu'on teste | Pourquoi c'est unitaire |
|---|---|---|
| `ZoneKeys.key()` | `key(zone)` retourne `"title_0"` | Fonction pure, pas de dépendance |
| `ContentCapacityCalculator.calculate()` | La capacité max est correcte | Calcul pur, pas de DB |
| `AiResponseParser.parse()` | Un JSON mal formé est toléré | Parsing pur |
| `BackgroundDetector.detect()` | Les zones de fond sont identifiées | Logique de détection |
| `DeterministicLayoutAssigner.assign()` | `TITLE_SLIDE` → bon layout | Règles déterministes |
| `OutlineContentGenerator.generate()` | Le contenu outline est correct | Pas d'IA, pas de DB |
| `OutputSchemaProvider.create()` | Le schéma contient les bonnes clés | Construction pure |

### Code d'exemple (JUnit 5)

```java
package com.pptxgenerator.model;

import com.pptxgenerator.model.enums.ZoneType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneKeysTest {

    @Test
    void key_returnsSnakeCaseType_underscore_zoneId() {
        // Arrange (préparer les données)
        Zone zone = Zone.builder()
                .zoneId(0)
                .zoneType(ZoneType.TITLE)
                .build();

        // Act (exécuter la méthode)
        String key = ZoneKeys.key(zone);

        // Assert (vérifier le résultat)
        assertEquals("title_0", key);
    }

    @Test
    void key_withBodyZone_returnsBody1() {
        Zone zone = Zone.builder()
                .zoneId(1)
                .zoneType(ZoneType.BODY)
                .build();

        assertEquals("body_1", ZoneKeys.key(zone));
    }
}
```

### Structure AAA (Arrange / Act / Assert)

Chaque test suit ce schéma :
1. **Arrange** : préparer les données d'entrée
2. **Act** : appeler la méthode testée
3. **Assert** : vérifier que le résultat est correct

C'est la structure universelle. Même si tu ne la vois pas explicitement dans le code,
elle est toujours là.

---

## Test d'intégration

### Définition

Un test d'intégration vérifie **une couche avec ses vraies dépendances**. On ne mock
que les externes (IA, stockage), pas les classes internes du même module.

**Règle** : si tu changes une dépendance directe, le test peut casser (et c'est voulu).

### Différence avec le test unitaire

| | Unitaire | Intégration |
|---|---|---|
| Dépendances | Mocks | Vraies (DB H2, docx4j, etc.) |
| Vitesse | < 1s | 1-10s |
| Périmètre | 1 classe | 1 couche (plusieurs classes) |
| Ce qu'on teste | La logique | La collaboration |

### Exemples concrets du projet

| Classe | Dépendances réelles | Ce qu'on vérifie |
|---|---|---|
| `TemplateAnalyzer` | docx4j (vrai PPTX) + IA mock | L'analyse produit des layouts avec zones et idx |
| `LayoutAssignmentService` | `DeterministicLayoutAssigner` + `AILayoutAssigner` (mock) | Le plan est enrichi avec les bons layouts |
| `ContentGenerationService` | `ParallelContentGenerator` (mock) | Le `GeneratedContent` a le bon nombre de slides |
| `PlaceholderMapper` | docx4j (vraies shapes) | Le matching zone→forme fonctionne |
| `PlaceholderInjector` | docx4j (vraies shapes) | Le texte atterrit dans la bonne forme |
| `PptxRenderEngine` | docx4j (vrai template) | Le PPTX final est produit sans erreur |

### Code d'exemple

```java
package com.pptxgenerator.pipeline.renderer;

import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.ZoneKeys;
import com.pptxgenerator.model.enums.ZoneType;
import org.docx4j.XmlUtils;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.junit.jupiter.api.Test;
import org.pptx4j.pml.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlaceholderMapperIntegrationTest {

    @Test
    void mapPlaceholders_matchesByIdx() throws Exception {
        // Arrange : créer une slide avec 2 placeholders
        SlidePart slidePart = buildSlideWithPlaceholders(
                placeholderXml(2, 0L, "title"),
                placeholderXml(3, 1L, "body"));

        List<Zone> zones = List.of(
                Zone.builder().zoneId(0).zoneType(ZoneType.TITLE).idx(0L).build(),
                Zone.builder().zoneId(1).zoneType(ZoneType.BODY).idx(1L).build());

        // Act
        Map<String, Shape> mapping = new PlaceholderMapper()
                .mapPlaceholders(slidePart, zones);

        // Assert
        assertEquals(2, mapping.size());
        assertTrue(mapping.containsKey("title_0"));
        assertTrue(mapping.containsKey("body_1"));
        assertEquals(0L, mapping.get("title_0").getNvSpPr().getNvPr().getPh().getIdx());
    }

    // Helpers (construisent des objets docx4j en mémoire)
    private SlidePart buildSlideWithPlaceholders(String... shapes) throws Exception {
        SlidePart slide = new SlidePart();
        slide.setContents(new Sld());
        slide.getContents().setCSld(new CommonSlideData());
        slide.getContents().getCSld().setSpTree(new GroupShape());
        for (String xml : shapes) {
            slide.getContents().getCSld().getSpTree()
                    .getSpOrGrpSpOrGraphicFrame()
                    .add((Shape) XmlUtils.unmarshalString(xml, Context.jcPML));
        }
        return slide;
    }

    private String placeholderXml(long id, long idx, String type) {
        return "<p:sp xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\""
                + " xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">"
                + "<p:nvSpPr>"
                + "<p:cNvPr id=\"" + id + "\"/>"
                + "<p:cNvSpPr/>"
                + "<p:nvPr><p:ph idx=\"" + idx + "\" type=\"" + type + "\"/></p:nvPr>"
                + "</p:nvSpPr>"
                + "<p:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"100\" cy=\"100\"/></a:xfrm></p:spPr>"
                + "<p:txBody/>"
                + "</p:sp>";
    }
}
```

---

## Test E2E (end-to-end)

### Définition

Un test E2E exécute **tout le pipeline** de bout en bout. On fournit un vrai template,
on mock l'IA, et on vérifie que le fichier PPTX final est correct.

**Règle** : si tu changes n'importe quelle étape du pipeline, le test peut casser.

### Quand l'utiliser ?

- **Rarement**. Ce sont des tests **lents** (10-30s) et **fragiles** (docx4j JAXB).
- Uniquement pour les **scénarios critiques** : "est-ce que le pipeline produit un PPTX lisible ?"
- On en met **peu** (2-3 max) pour couvrir les cas principaux.

### Exemples concrets du projet

| Scénario | Template | IA mock | Ce qu'on vérifie |
|---|---|---|---|
| Pipeline complet | Template titre + body | Contenu généré | Le PPTX contient le texte injecté |
| Template minimal | 1 slide titre | Titre généré | La slide titre a le bon texte |
| Pas de zone body | Template titre seul | N/A | Le renderer produit un warning (pas une erreur) |

### Pourquoi ils sont désactivés dans ce projet ?

Les tests E2E renderer (`PptxRenderEngineManualTest`, `RealPipelineRenderTest`)
utilisent docx4j avec la variante JAXB MOXy, qui a un bug connu :
`namespacePrefixMapper is null` lors du `save()`. Ces tests compilent mais sont
annotés `@Disabled` en attendant la correction.

### Code d'exemple (conceptuel)

```java
@Disabled("docx4j JAXB namespacePrefixMapper issue — à réactiver quand corrigé")
class PipelineE2ETest {

    @Test
    void fullPipeline_producesValidPptx() throws Exception {
        // Arrange : template minimal en mémoire
        String templatePath = createMinimalTemplate(); // helper qui crée un .pptx
        TemplateAnalysis analysis = analyzeTemplate(templatePath); // M1
        PresentationPlan plan = generatePlan(); // M2 (IA mock)
        PlanWithLayouts layouts = assignLayouts(plan, analysis); // M3
        GeneratedContent content = generateContent(layouts); // M4 (IA mock)

        // Act : render
        String outputPath = "target/e2e-test.pptx";
        RenderResult result = engine.render(templatePath, analysis, content, outputPath);

        // Assert
        assertEquals(1, result.getTotalSlides());
        assertTrue(result.getOutputFile().exists());
        assertTrue(result.getWarnings().isEmpty());
    }
}
```

---

## Tableau de classification du projet

| Classe | Type de test | Justification |
|---|---|---|
| `ZoneKeys` | **Unitaire** | Fonction pure, pas de dépendance |
| `OutputSchemaProvider` | **Unitaire** | Construction pure de schéma |
| `ContentCapacityCalculator` | **Unitaire** | Calcul pur |
| `BackgroundDetector` | **Unitaire** | Logique de détection |
| `AiResponseParser` | **Unitaire** | Parsing pur |
| `SystemPromptLibrary` | **Unitaire** | Concaténation de fragments |
| `DeterministicLayoutAssigner` | **Unitaire** | Règles déterministes |
| `FallbackStrategy` | **Unitaire** | Logique de repli |
| `OutlineContentGenerator` | **Unitaire** | Pas d'IA, pas de DB |
| `SectionTransitionContentGenerator` | **Unitaire** | Pas d'IA, pas de DB |
| `ContentPromptBuilder` | **Unitaire** | Construction de prompt |
| `PlaceholderMapper` | **Intégration** | Nécessite docx4j (vraies shapes) |
| `PlaceholderInjector` | **Intégration** | Nécessite docx4j (vraies shapes + XML) |
| `SlideFactory` | **Intégration** | Nécessite docx4j (vrai layout) |
| `TemplateAnalyzer` | **Intégration** | Nécessite docx4j + IA mock |
| `LayoutAssignmentService` | **Intégration** | Combine assigner déterministe + IA |
| `ContentGenerationService` | **Intégration** | Combine générateurs parallèles |
| `PptxRenderEngine` | **Intégration** | Nécessite docx4j (template complet) |
| `ContentCreationPipeline` | **E2E** | Pipeline complet M1→M5 |
| `ContentService` | **Intégration** | Nécessite DB H2 |
| `TemplateService` | **Intégration** | Nécessite DB H2 + MinIO |

---

## Bonnes pratiques

### 1. Nommage des tests

Le nom du test doit décrire **ce qu'on teste** et **le scénario** :

```java
// ✅ Bon — on comprend quoi et pourquoi
void key_returnsSnakeCaseType_underscore_zoneId() { ... }
void mapPlaceholders_whenIdxNotFound_returnsEmpty() { ... }

// ❌ Mauvais — on comprend rien
void test1() { ... }
void testKey() { ... }
void shouldWork() { ... }
```

Convention : `methodName_condition_expectedResult`

### 2. Un test = une seule assertion conceptuelle

```java
// ✅ Bon — teste une seule chose
@Test
void key_returnsTitle0() {
    assertEquals("title_0", ZoneKeys.key(titleZone));
}

// ❌ Mauvais — teste 3 choses différentes
@Test
void testZoneKeys() {
    assertEquals("title_0", ZoneKeys.key(titleZone));
    assertEquals("body_1", ZoneKeys.key(bodyZone));
    assertEquals("footer_2", ZoneKeys.key(footerZone));
}
```

**Exception** : les asserts multiples sont OK si ils testent le **même concept**
(ex: "la map contient les 2 clés" = 1 concept).

### 3. Les mocks (concept)

Un **mock** est un objet factice. Au lieu d'utiliser la vraie dépendance (DB, IA, MinIO),
on crée un objet qui fait semblant :

```java
// Le vrai service a besoin d'une DB
@ApplicationScoped
public class ContentService {
    private final ContentRepository repository; // dépendance
}

// Dans le test, on mock le repository
ContentRepository mockRepo = Mockito.mock(ContentRepository.class);
when(mockRepo.findByContentId("123")).thenReturn(someContent);

// Le service utilise le mock au lieu de la vraie DB
ContentService service = new ContentService(mockRepo);
```

**Règle** : on mock **une seule couche**. Si tu mocks 5 dépendances, c'est que tu
testes probablement trop de choses en même temps → découpe en tests unitaires.

### 4. Les tests de non-régression

Quand tu corriges un bug, **ajoute un test qui vérifie que le bug ne revient pas** :

```java
// Bug corrigé : la clé de zone était UPPERCASE au lieu de lowercase
@Test
void zoneKey_isLowerCase_andMatchesGeneratorSchema() {
    Zone title = Zone.builder().zoneId(0).zoneType(ZoneType.TITLE).build();
    assertEquals("title_0", ZoneKeys.key(title));

    Map<String, Object> schema = OutputSchemaProvider
            .createSlideContentSchema(List.of(title));
    assertTrue(((Map<?, ?>) schema.get("properties")).containsKey("title_0"));
}
```

Ce test **doit rester vert éternellement**. S'il redevient rouge, c'est qu'on a
cassé le format de clé.

---

## Pièges courants

### ❌ "C'est un test unitaire mais il mock 8 dépendances"

→ Ce n'est **pas** un test unitaire. C'est un test d'intégration mal déguisé.
Découpe-le en tests unitaires plus petits, ou accepte que c'est un test d'intégration.

### ❌ "Mon test est lent (> 1s pour un unitaire)"

→ Cherche ce qui est lent. Souvent c'est :
- docx4j (construction d'objets PPTX) → c'est un test d'intégration, pas unitaire
- Appel réseau → mock l'appel
- Parsing JSON complexe → générateur de données plus simple

### ❌ "Mon test casse quand je change une autre classe"

→ Le test teste **trop de choses**. Réduit le périmètre à la seule classe testée.

### ❌ "Les tests E2E cassent tout le temps"

→ Les tests E2E sont **fragiles par nature** (docx4j, JAXB, IA). Garde-les peu
nombreux et concentre-toi sur les tests unitaires + intégration.

### ❌ "J'ai pas compris pourquoi on mock l'IA"

→ L'IA est **non déterministe** : elle peut retourner du JSON valide, invalide, ou
timeout. Dans les tests, on veut des résultats **prévisibles**. D'où le mock :
`when(ia.processRequest(any())).thenReturn(jsonValide)`.

---

## Résumé en une phrase

> **Test unitaire** = teste la logique (pas de dépendances).
> **Test d'intégration** = teste la collaboration (vraies dépendances).
> **Test E2E** = teste le flux complet (tout le pipeline).
> Commence par les unitaires, ajoute l'intégration pour les couches critiques,
> et garde les E2E pour les scénarios principaux.
