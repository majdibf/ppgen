# Testing Rules — pptx_generator

> Ces règles sont **dérivées des sources** du projet : `pom.xml`, `docs/TESTING.md`
> et `src/test/resources/application.properties`. ⚠️ **Aucun fichier de test `.java`
> n'existe actuellement dans `src/test/java`** (arbre de travail vide côté tests).

---

## 1. Frameworks et dépendances (OBSERVÉ — `pom.xml`)

- **JUnit 5** via `quarkus-junit5` (scope test) — `pom.xml:150-154`
- **REST-assured** pour les tests d'API — `pom.xml:155-159`
- **H2** (DB en mémoire) pour les tests — `pom.xml:160-164`, config `src/test/resources/application.properties:5-7`


Note : Mockito n'apparaît **pas** comme dépendance directe dans `pom.xml`, mais
`docs/TESTING.md` l'utilise dans ses exemples (`docs/TESTING.md:358`). **À VALIDER**
(probable inclus via Quarkus, ou à ajouter — aucun test ne le démontre aujourd'hui).

## 2. Configuration de test (OBSERVÉ — `src/test/resources/application.properties`)

- DB **H2** en mémoire avec `DB_CLOSE_DELAY=-1`, `drop-and-create`
  (`application.properties:5-7`)
- **Flyway désactivé** en test (`quarkus.flyway.migrate-at-start=false`, `:10`)
- **IA mockée** : `app.ai.mock=true`, provider `groq` (`:13-14`)
- MinIO configuré mais commenté « disabled for tests » (`:16-21`)
- Logging WARN (DEBUG pour `com.pptxgenerator`)

## 3. Trois niveaux de tests (OBSERVÉ — `docs/TESTING.md`)

Le contrat de classification est explicite dans `docs/TESTING.md:6-33` :

| Niveau | Périmètre | Dépendances | Vitesse |
|---|---|---|---|
| Unitaire | 1 classe | mocks | < 1 s |
| Intégration | 1 couche | DB H2 / vrai docx4j / vrai template | 1–10 s |
| E2E | pipeline M1→M5 | vrai template + IA mock | 10–30 s |

## 4. Convention de nommage (OBSERVÉ — `docs/TESTING.md:307-322`)

- Fichiers : suffixe `Test` (ex. `ZoneKeysTest`), `IntegrationTest` pour l'intégration.
- Méthodes : `methodName_condition_expectedResult`
  (ex. `key_returnsSnakeCaseType_underscore_zoneId`).
- Éviter `test1()`, `testKey()`, `shouldWork()`.

## 5. Structure AAA (Arrange / Act / Assert) (OBSERVÉ — `docs/TESTING.md:103-111`)

Chaque test suit explicitement `// Arrange` → `// Act` → `// Assert`
(ex. `docs/TESTING.md:77-98`).

## 6. Stratégie d'assertion (OBSERVÉ — `docs/TESTING.md`)

- Assertions JUnit : `assertEquals`, `assertTrue`, `assertNotNull` via imports statiques
  (`docs/TESTING.md:66-98`, `:181-184`).
- **Un test = une seule assertion conceptuelle** (`docs/TESTING.md:324-343`), sauf cas
  testant le même concept (ex. « la map contient les 2 clés »).

## 7. Stratégie de mock (OBSERVÉ — `docs/TESTING.md:345-367`)

- L'IA (non déterministe) est **mockée** pour des réponses prévisibles (`:412-417`).
- Le stockage MinIO est mocké (commenté comme disabled en test).
- Règle : **ne mock qu'une seule couche** ; mock de >5 dépendances = mauvais périmètre.
- Mock d'une dépendance via `Mockito.mock(...)` + `when(...).thenReturn(...)`
  (`docs/TESTING.md:358-362`).

## 8. Fixtures / builders réutilisables (OBSERVÉ — `docs/TESTING.md`)

- Construction d'objets de domaine via **Lombok builders** : `Zone.builder().zoneId(0)...`
  (`docs/TESTING.md:79-83`).
- Helpers « in-memory » pour docx4j : `buildSlideWithPlaceholders(...)`,
  `placeholderXml(...)` (`docs/TESTING.md:188-213`) — construisent des objets docx4j sans fichier.
- L'IA mockée prédéterminée est fournie par `client/helper/AIMockProvider`
  (`src/main/java/com/pptxgenerator/client/helper/AIMockProvider.java`) — sert de faux
  backend IA pour les tests (réponse de plan JSON prédéfinie).

## 9. Classification projetée des classes (OBSERVÉ — `docs/TESTING.md:277-301`)

Tableau de classification fourni dans le doc :
- **Unitaire** : `ZoneKeys`, `OutputSchemaProvider`, `ContentCapacityCalculator`,
  `BackgroundDetector`, `AiResponseParser`, `SystemPromptLibrary`,
  `DeterministicLayoutAssigner`, `FallbackStrategy`, `ContentPromptBuilder`, générateurs outline/section.
- **Intégration** : `PlaceholderMapper`, `PlaceholderInjector`, `SlideFactory`,
  `TemplateAnalyzer`, `LayoutAssignmentService`, `ContentGenerationService`, `PptxRenderEngine`,
  `ContentService`, `TemplateService`.
- **E2E** : `ContentCreationPipeline`.

> ⚠️ Ces classes référencées (`PptxRenderEngineManualTest`, `RealPipelineRenderTest`,
> `ZoneKeysTest`, …) et les tests E2E renderer **n'existent pas dans le worktree**.
> Les classifications ci-dessus reflètent le **contrat documenté**, pas des tests présents.
> Certains responsabilités/catégories (ex. `SemanticZoneNamer`, `ZoneDescriptionEnricher`)
> ont été restructurés dans `pipeline/...` ; à ré-aligner lors de la rédaction de tests réels.

## 10. Tests désactivés (OBSERVÉ)

`docs/TESTING.md:241-246` documente que les tests E2E renderer utilisant docx4j (variante
JAXB MOXy) sont annotés `@Disabled("docx4j JAXB namespacePrefixMapper issue — ...")` en
attendant la correction du `save()` (`namespacePrefixMapper is null`). Ce bug connu est aussi
reporté dans `AGENTS.md`.

## 11. Commandes Maven (OBSERVÉ — `AGENTS.md`, `pom.xml`)

```bash
mvn test        # exécute les tests unitaires + surefire
mvn compile     # vérifie la compilation
mvn quarkus:dev # mode dev (FAKE MySQL non requis)
```

Le plugin surefire est configuré avec les factories JAXB pour docx4j
(`pom.xml:215-226`) — requis pour les tests renderer.

## 12. Exemples représentatifs (basés sur `docs/TESTING.md`)

**Test unitaire pur :**
```java
@Test
void key_returnsSnakeCaseType_underscore_zoneId() {
    Zone zone = Zone.builder().zoneId(0).zoneType(ZoneType.TITLE).build();
    String key = ZoneKeys.key(zone);
    assertEquals("title_0", key);
}
```

**Test d'intégration docx4j (conceptuel, avec helpers in-memory) :**
```java
@Test
void mapPlaceholders_matchesByIdx() throws Exception {
    SlidePart slidePart = buildSlideWithPlaceholders(
            placeholderXml(2, 0L, "title"), placeholderXml(3, 1L, "body"));
    List<Zone> zones = List.of(
            Zone.builder().zoneId(0).zoneType(ZoneType.TITLE).idx(0L).build(),
            Zone.builder().zoneId(1).zoneType(ZoneType.BODY).idx(1L).build());
    Map<String, Shape> mapping = new PlaceholderMapper().mapPlaceholders(slidePart, zones);
    assertEquals(2, mapping.size());
    assertTrue(mapping.containsKey("title_0"));
}
```

> ⚠️ Ces exemples proviennent du guide `docs/TESTING.md` ; ils n'ont **pas** été exécutés
> dans ce dépôt (aucune classe de test présente).

---

## Résumé — éléments nécessitant validation humaine

Voir le résumé final dans la réponse de l'agent (section « Résumé des éléments à valider »).
