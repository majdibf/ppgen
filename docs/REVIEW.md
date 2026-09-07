# Revue / Brainstorm — pptx_generator

> Document de travail itératif. Chaque section est numérotée pour pouvoir être référencée
> depuis une conversation (`R3.2`, `D2`, `T4`). Les points marqués **🔴 fort** me dérangent
> vraiment, **🟡 moyen** sont des irritants à clarifier, **🟢 léger** sont des observations.
>
> Date d'ouverture : 2026-09-04. Référence projet : branche `main`, worktree contient des
> modifications non commitées (voir section 1).

---

## 1. Contexte rapide

### 1.1 Ce que fait le projet
Application Quarkus 3.8.1 / Java 21 / Maven single-module. Pipeline séquentiel 5 étapes :
M1 `analyzer` → M2 `planner` → M3 `assigner` → M4 `generator` → M5 `renderer`. API REST
asynchrone (statut côté DB, exécution pipeline sur `ExecutorService` maison), stockage MinIO,
deux providers IA au choix (Groq, OpenRouter) plus un mock. Détails complets dans
`.ai-workflow/architecture.md`.

### 1.2 État du worktree
- Modifs non commitées :
  - `pipeline/generator/SlideContentGenerator.java` (modifié)
  - `pipeline/generator/ZoneClassifier.java` (nouveau)
  - `pipeline/renderer/PlaceholderMapper.java` (modifié)
  - `pipeline/renderer/PptxRenderEngine.java` (modifié)
  - `pipeline/renderer/OoxmlHelper.java` (nouveau)
  - `pipeline/renderer/PlaceholderInjector.java` (supprimé)
  - `pipeline/renderer/PlaceholderInjectorTest.java` (supprimé)
  - `PlaceholderMapperInjectionTest.java` (nouveau)
  - `PlaceholderMapperTest.java` (modifié)
- Supprimés staged : `AGENTS.md`, `Renderengine.md`, `output_*.pptx`, `spec.md`.
- Non-Maven hors build : `rendererbpce/`, `other_codes/`, `python_poc/`, `gv2/`, `rv2/`,
  `pl-example/`, `target/`, `template_1.pptx`. Le `python_poc/` semble être la référence
  d'origine (cf. javadoc de `TemplateAnalyzer.java:27` — *« Java port of other_codes/step2_layout.py »*).

### 1.3 État des tests
Aucun test n'est exécuté en CI ni reproductible localement — `docs/TESTING.md` décrit un
contrat (unitaire / intégration / E2E, AAA, `methodName_condition_expectedResult`, mock IA
uniquement) mais **les classes de test référencées n'existent pas** dans le worktree (cf.
`.ai-workflow/testing-rules.md` §10). `mvn test` ne tournerait rien d'utile aujourd'hui. Les
seuls tests présents sont ceux ajoutés/modifiés autour de `PlaceholderMapper`.

### 1.4 Documents existants
- `.ai-workflow/architecture.md` — vue d'ensemble citée en référence.
- `.ai-workflow/conventions.md` — conventions observées + exceptions + « à ne pas reproduire ».
- `.ai-workflow/testing-rules.md` — règles de tests dérivées du contrat.
- `docs/ZONE_PIPELINE.md` — **excellente** analyse de la chaîne `Zone` (analyser → générer →
  rendre), avec 5 pistes d'amélioration déjà hiérarchisées (E, C, A2, A3, B, D).
- `docs/TESTING.md`, `docs/ERROR_HANDLING.md` — guides, désalignés avec le code réel.

> ⚠️ Le présent document ne duplique pas `ZONE_PIPELINE.md`. Il **débute** la revue et
> renvoie explicitement à ce doc pour tout ce qui touche à la chaîne Zone.

---

## 2. Ce qui me dérange (R1–R6)

### R1 — 🔴 Chaîne `Zone` : 4 jointures muettes, échec silencieux

Référence : `docs/ZONE_PIPELINE.md` §6 (séquence + résumé) et §8 (pistes).
- 4 jointures indépendantes : `layoutId` → `originalName` → cascade géométrique idx/type/pos/dim → `ZoneKey`.
- Chacune peut échouer, **chacune dégrade en `log.warn`**, jamais d'exception → un PPTX
  incomplet sort sans qu'aucun signal clair ne dise *où* la chaîne a décroché.
- Piste la plus simple à exécuter d'abord : **E** (rendre les échecs bruyants). Le contrat
  `docs/ZONE_PIPELINE.md` §8 suggère l'ordre E → C → A2 → A3 → B → D.

> 💬 À trancher : on s'engage sur E et C cette session, ou on attend ?

### R2 — 🔴 `zoneId` n'est pas un identifiant stable (effet de bord du parcours XML)
Référence : `docs/ZONE_PIPELINE.md` §1, §3, §7.
- Le compteur `zoneId++` dans `TemplateAnalyzer.identifyZones`
  (`pipeline/analyzer/TemplateAnalyzer.java:141, 188`) dépend de l'ordre des balises XML.
- Ce même `zoneId` est la moitié de `ZoneKey` et la clé de tri du chemin déterministe en
  génération → un réenregistrement du template sous PowerPoint renumérote silencieusement.
- Piste B (`ZoneKey` basé sur `idx`) est la correction propre, mais demande un commit
  atomique des deux côtés (generator + renderer).

> 💬 À trancher : on valide l'hypothèse produit *« template immuable entre deux
> utilisations »* ? Si oui, A2 suffit et B devient optionnel.

### R3 — 🔴 Cascade de replis au rendu = `get(0)` arbitraire et muet
Référence : `docs/ZONE_PIPELINE.md` §5b, `pipeline/renderer/PlaceholderMapper.java:142`.
- Étapes idx → type → position (tolérance 100 000 EMU) → dimension (idem) → `get(0)` muet.
- Le fichier importe `java.util.*` (`PlaceholderMapper.java:18`) et `Comparator` n'est jamais
  utilisé — trace d'un tri envisagé puis abandonné.
- Le `get(0)` final ne journalise même pas qu'il y avait ambiguïté → bug le plus
  imprévisible du pipeline.

> 💬 Action immédiate proposée : log explicite quand on tombe sur le `get(0)` + compteur
> dans le warning « combien de candidats étaient en lice ».

### R4 — 🟡 Async pipeline : `ExecutorService` maison + activation manuelle du contexte CDI
Référence : `service/ContentService.java:46, 112-129`.
- `Executors.newFixedThreadPool(5)` créé dans le constructeur du service → jamais fermé,
  taille figée, pas de nom de thread identifiable, pas de back-pressure.
- `Arc.container().requestContext().activate()` / `terminate()` à la main = fragile.
- Quarkus offre `@RunOnVirtualThread` (Java 21 ✓), `quarkus-messaging`, ou un `@Scheduled`
  drain. Réinventer le pool est une dette connue (cf. `.ai-workflow/architecture.md` §9.5).

> 💬 À trancher : on garde le pattern actuel + on extrait un `PipelineExecutor` nommé, ou on
> migre vers Mutiny / messaging ?

### R5 — 🟡 `ObjectMapper` instancié 3+ fois, `processAsync` mort, champs publics
- `new ObjectMapper()` directement :
  - `service/ContentService.java:44`
  - `pipeline/ContentCreationPipeline.java:49, 54`
  - `client/GroqGenerativeAiApi.java:32`
  - `client/OpenRouterGenerativeAiApi.java` (à confirmer)
- `ContentCreationPipeline.processAsync` (`pipeline/ContentCreationPipeline.java:76-98`) :
  existe, n'est jamais appelé (recherche `processAsync` dans le repo).
- Champs `@ConfigProperty public` dans `GroqGenerativeAiApi.java:34-41` et
  `GenerativeAiGateway.java:31-32` → pas d'encapsulation, mutable de l'extérieur.
- `GenerativeAiGateway` mélange `@Inject` sur champ (`:20, 23`) et constructeur implicite
  (`@ApplicationScoped` sans `@RequiredArgsConstructor`) → incohérent avec le reste du code.

> 💬 Action immédiate : injecter `ObjectMapper` CDI partout (Quarkus en fournit un
> préconfiguré), supprimer `processAsync`, privatiser les champs.

### R6 — 🟡 Documentation ↔ code désynchronisés
- `AGENTS.md` (supprimé du worktree mais référencé historiquement) décrivait des packages
  racine `analyzer/`, `planner/`, etc. — code réel sous `pipeline/...`. La refonte a eu
  lieu, la doc n'a pas suivi.
- `Renderengine.md`, `spec.md` supprimés — si c'étaient des specs de référence, les perdre
  sans commit explicite est risqué.
- `docs/TESTING.md` parle de classes qui n'existent pas (`ZoneKeysTest`, etc.) —
  `.ai-workflow/testing-rules.md` §10 le pointe.
- `opencode.jsonc` et `.opencode/rules/project-quality.md` → conformes, pas de souci.

> 💬 Action immédiate : supprimer `docs/TESTING.md` obsolète ou le réécrire à partir du
> code réel + tests absents. Garder `ZONE_PIPELINE.md` qui est la seule référence à jour.

---

## 3. Observations complémentaires (R7–R11)

### R7 — 🟢 `ZoneClassifier` tout neuf mais déjà sous-utilisé
- `pipeline/generator/ZoneClassifier.java` (nouveau) factorise les listes filtrées par type.
- Bonne intention, mais :
  - Les méthodes `getLineZones()` / `getWordZones()` trient par `zoneId` — c'est précisément
    R2 : le tri dépend d'un identifiant non stable. Si R2 est corrigé (B), ce tri doit
    être remplacé.
  - `getFirstTitle()` essaie TITLE puis CENTER_TITLE, mais retourne `Optional<Zone>` et
    n'a aucune notion de « la zone la plus haute » — donc pour les templates où TITLE
    existe en bas + CENTER_TITLE en haut, le résultat est l'ordre de parcours, pas la
    position.
- Centraliser est une bonne idée, mais le classifier cache une décision métier implicite
  (« TITLE > CENTER_TITLE > SUBTITLE »). À documenter ou à paramétrer.

### R8 — 🟢 `OoxmlHelper` : bonne extraction, mais classe interne `ParagraphStyle`
- `pipeline/renderer/OoxmlHelper.java:230-331` : inner class `ParagraphStyle` avec builder
  ET factories statiques (`simple()`, `bullet()`, `centered()`, `withAlignment(...)`) —
  deux APIs pour le même objet. Garder une seule.
- `parseBulletStyle` (`OoxmlHelper.java:214-222`) : parse les préfixes `"  - "`, `"- "`,
  `"  • "`, `"• "` → couplage fort avec le format produit par l'IA en amont (prompts).
  Fragile si l'IA change de format. À déplacer vers une convention documentée + validateur.

### R9 — 🟢 `SlideContentGenerator` : chemins IA et déterministes, deux philosophies
- IA (`generateWithAI` `pipeline/generator/SlideContentGenerator.java:133-165`) : clé
  explicite par `ZoneKey` imposée via schéma.
- Déterministe (`generateOutlineContent`, `generateSectionTransitionContent`) : tri par
  `zoneId` puis zip positionnel.
- Exactement le point D de `docs/ZONE_PIPELINE.md`. À unifier.

### R10 — 🟢 `PlaceholderMapper` vs ancien `PlaceholderInjector`
- L'ancien `PlaceholderInjector` a été supprimé et sa logique semble répartie entre
  `PlaceholderMapper` (mapping + injection) et `OoxmlHelper` (helpers bas niveau).
- Risque : `PlaceholderMapper` fait maintenant deux responsabilités (mapping **et**
  injection), ce qui contredit `.ai-workflow/conventions.md` « une responsabilité = une
  classe ». La javadoc le dit (`PlaceholderMapper.java:21-27`).

> 💬 À trancher : on garde ce couplage tant que `OoxmlHelper` existe, ou on sépare à
> nouveau `PlaceholderMapper` (lecture seule) / `PlaceholderInjector` (écriture) ?

### R11 — 🟢 `LayoutAssignmentService` : 4 stratégies empilées sans hiérarchie claire
- `DeterministicLayoutAssigner` + `AILayoutAssigner` + `FallbackAssignment` +
  `LayoutAssignmentValidator`. Bonne séparation.
- Le service orchestrateur (`LayoutAssignmentService.java:96-121`) :
  - `deterministic.optional → sinon IA → sinon fallback terminal → sinon `get(0)``.
  - Le `get(0)` muet de `:118-120` est le même anti-pattern que R3, dans un autre stage.
- `FallbackAssignment.findUltimateFallback` (`FallbackAssignment.java:31-53`) ré-implémente
  une partie de la logique de `DeterministicLayoutAssigner` (les `findFirstByType`
  TITLE/SECTION/OUTLINE) → duplication potentielle.

---

## 4. Pistes d'amélioration (P1–P8)

> Ordre suggéré = risque décroissant → ROI croissant.

### P1 — Rendre les échecs de jointure bruyants (R1, R3, R6) — risque ~0, gain d'observabilité
Référence : `docs/ZONE_PIPELINE.md` §8.E.
- Ajouter dans `RenderWarning` un champ `cascadeStage` (« IDX_MATCH », « TYPE_MATCH »,
  « POSITION_MATCH », « DIMENSION_MATCH », « ARBITRARY_GET0 »).
- Log explicite quand `get(0)` est atteint + nombre de candidats en lice.
- Ajouter dans `LayoutAssignmentResult` un champ `cascadeStage` symétrique.

### P2 — Stabiliser l'identité de zone (R2, R7) — risque modéré
Référence : `docs/ZONE_PIPELINE.md` §8.A2 + §8.B.
- Valider d'abord la règle produit : **un template uploadé n'est jamais modifié sans être
  ré-analysé**. Si vrai, A2 suffit (pas besoin de A1 / réécrire le PPTX).
- Si oui : garantir que `TemplateAnalyzer.identifyZones` et le rendu utilisent **la même
  fonction** pour numéroter les `idx` manquants. Aujourd'hui, l'analyse pose un `idx` réel
  quand il existe (via `safeIdx`), le rendu n'a rien quand il manque → forcer un calcul
  partagé.
- Diff avant/après sur `template_analysis.json` de debug (cf. dernier paragraphe de
  `ZONE_PIPELINE.md`) est le test de non-régression.

### P3 — Réinjecter `ObjectMapper` CDI, supprimer `processAsync`, privatiser les champs (R5)
- Risque bas, code répétitif, pattern uniforme.
- `ObjectMapper` Quarkus est déjà configuré pour Jackson + LocalDateTime + snake_case si
  on le configure.
- `processAsync` est du code mort à supprimer.

### P4 — Refondre l'async pipeline (R4)
- Soit extraire un `PipelineExecutor` nommé (faible coût, pas de révolution).
- Soit migrer vers Mutiny / `@RunOnVirtualThread` (coût moyen, plus de cohérence Quarkus).
- À arbitrer avec la question : veut-on de la back-pressure (messaging) ou pas ?

### P5 — Séparer `PlaceholderMapper` (mapping) / injection (R10)
- Si on garde la séparation `OoxmlHelper` (bas niveau) + `Mapper` + `Injector`, on respecte
  la convention « une classe = une responsabilité ».
- Si on garde le couplage, le documenter clairement dans la javadoc et accepter le
  compromis.

### P6 — Unifier le matching generator : IA + déterministe (R9) — référence `ZONE_PIPELINE.md` §8.D
- Le déterministe doit choisir explicitement la zone cible par `ZoneKey`/`idx`, pas par
  tri + zip.
- Demande de figer pour chaque générateur déterministe quelle zone reçoit quel rôle.

### P7 — Documenter le classifier + le rendre paramétrable (R7)
- Documenter la hiérarchie TITLE > CENTER_TITLE > SUBTITLE, ou la paramétrer par layout.

### P8 — Nettoyer le repo (R6)
- Supprimer les dossiers non-Maven (`rendererbpce/`, `other_codes/`, `python_poc/`, `gv2/`,
  `rv2/`, `pl-example/`) ou les isoler dans `archive/` + `.gitignore` (le `python_poc/`
  semble servir de référence → le déplacer en `docs/reference/`).
- Supprimer `output_*.pptx` du commit historique (déjà fait via staged delete).
- Réécrire ou supprimer `docs/TESTING.md`, `docs/AGENTS.md` si encore présents ailleurs.
- Garder `template_1.pptx` s'il sert de fixture de test, sinon l'extraire.

---

## 5. Décisions à trancher (D1–D6)

| # | Question | Mon avis par défaut |
|---|---|---|
| D1 | On s'engage sur **P1** (rendre les échecs bruyants) cette session ? | Oui, risque ~0, ROI observabilité immédiat. |
| D2 | Règle produit : un template uploadé est-il **immuable** entre deux utilisations ? | Si oui → A2 suffit ; si non → A1 nécessaire (réécrire le PPTX). |
| D3 | `processAsync` est supprimé ou migré ? | Supprimé (code mort). |
| D4 | Async pipeline : refactor minimal ou migration Mutiny ? | Minimal (`PipelineExecutor` nommé) cette session, migration Mutiny plus tard. |
| D5 | `PlaceholderMapper` redevient 2 classes ? | Oui (séparation mapping / injection) — respecte la convention du projet. |
| D6 | `ObjectMapper` injecté CDI vs `new ObjectMapper()` ? | Injecté partout, sans exception. |

---

## 6. TODO actionnable

- [ ] **T1** (R5, P3) — Remplacer tous les `new ObjectMapper()` par injection CDI.
- [ ] **T2** (R5) — Supprimer `ContentCreationPipeline.processAsync` + son appel mort.
- [ ] **T3** (R5) — Privatiser les `@ConfigProperty public` (Groq + OpenRouter + Gateway).
- [ ] **T4** (R1, R3, P1) — Ajouter `cascadeStage` aux warnings renderer + assigner.
- [ ] **T5** (R2, P2) — Décider D2 ; si oui, extraire `ZoneIdentityResolver` partagé.
- [ ] **T6** (R4, P4) — Extraire un `PipelineExecutor` nommé (au minimum), fermer le pool à
      l'arrêt Quarkus (`@Shutdown`).
- [ ] **T7** (R6, P8) — Nettoyer les artefacts non-Maven, mettre à jour `docs/`.
- [ ] **T8** (R10, P5) — Découper `PlaceholderMapper` si D5 = oui.
- [ ] **T9** (R7) — Documenter ou paramétrer `ZoneClassifier`.
- [ ] **T10** — Premier test minimal sur la cascade du `PlaceholderMapper` (fixture
      `template_1.pptx` → diff `template_analysis.json`) avant toute modif de la chaîne.

---

## 7. Ce que ce document n'aborde pas (à ouvrir dans une autre session si besoin)

- Choix des providers IA (Groq vs OpenRouter) — pas de coût, pas de lock-in flagrant.
- Politique de stockage MinIO (buckets, lifecycle, signatures) — `StoragePort` isole bien.
- API publique REST — `ContentController` est mince, le contrat semble sain.
- Modèle de données (`Content`, `Template`) — non inspecté en détail dans cette session.
- Sécurité des signatures `signatureSendDocument` / `signatureFetchResult` — protocole
  opaque, à creuser.

---

## 8. Références croisées

- `.ai-workflow/architecture.md` — vision d'ensemble (couches, flux A/B, dépendances).
- `.ai-workflow/conventions.md` — conventions + « à ne pas reproduire » (à mettre à jour
  avec les décisions de cette session).
- `.ai-workflow/testing-rules.md` — règles tests (rappel : aucun test n'existe).
- `docs/ZONE_PIPELINE.md` — analyse détaillée de la chaîne Zone + 5 pistes (E, C, A2,
  A3, B, D). **Référence principale pour R1, R2, R3.**
- `pom.xml:215-226` — config surefire (factories JAXB) indispensable aux tests renderer.