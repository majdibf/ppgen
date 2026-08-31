# Conventions — pptx_generator

> Conventions observées dans le code réel du worktree. Chaque règle cite un fichier.
> Les conventions sont classées : **dominante** (pratique majoritaire), **exceptions**
> (pratiques contraires présentes), **problématiques** (à ne pas reproduire).

---

## 1. Packages

**Dominante — package racine `com.pptxgenerator`**, regroupement par responsabilité
fonctionnelle (api, service, pipeline/stade, client, common, dto, entity, repository,
mapper, model, storage) (`pom.xml:7`).

- Sous-packages des stades du pipeline : `pipeline/<stade>` avec sous-packages `model`
  pour leurs DTO de domaine : `pipeline/planner/model`, `pipeline/assigner/model`,
  `pipeline/generator/model`, `pipeline/renderer/model`, `pipeline/assigner/ai`
  (`src/main/java/com/pptxgenerator/pipeline/...`).
- Clients internes : `client/dto`, `client/helper` (`client/GroqGenerativeAiApi.java`).
- Énumérations isolées : `model/enums` (`model/enums/ContentStatus.java`).
- Exceptions : `common/exception` (`common/exception/AIPipelineException.java`).

**Exception — dossiers hors package racine** : `rendererbpce/` sous `fr.bpce...`
(prototype, hors build) — ne pas utiliser comme référence de package.

**Problématique — package `config/` vide** (`src/main/java/com/pptxgenerator/config`)
et fichiers `MinimalRenderer.java`/`PoiRealisticRenderer.java` dans le **package par
défaut** de `src/main/java` (non alignés sur `com.pptxgenerator`).

---

## 2. Nommage des classes, méthodes et variables

**Dominante — conventions Java standard** :
- Classes : `PascalCase` (types ou rôles) : `ContentService`, `SlideFactory`,
  `PlanningPromptBuilder`, `PptxRenderEngine`.
- Méthodes/variables : `camelCase` : `getContent`, `uploadDocument`, `minSlides`
  (ex. `service/ContentService.java`).
- Suffixes de rôle fréquents : `*Service`, `*Repository`, `*Mapper`, `*Controller`,
  `*DTO` (DTOs sous `dto/`), `*Validator`, `*Builder` (prompts), `*Generator`, `*Analyzer`.
- Interfaces de contrat par rôle : `StoragePort` (port), `GenerativeAiApi` (interface IA).
- Enumérations : `SCREAMING_SNAKE_CASE` (`ContentStatus.WAITING_DOCUMENT`, `api/…/ContentStatus.java`).

**Exception** : champs publics avec `@ConfigProperty` dans des classes CDI
(`client/GroqGenerativeAiApi.java:34-41`, `client/GenerativeAiGateway.java:32`) au lieu de
champs privés. Style `public apiUrl`, `public apiKey`.

---

## 3. Injection de dépendances

**Dominante — CDI Quarkus (ArC)** :
- Beans scopés `@ApplicationScoped` (`service/ContentService.java:26`,
  `pipeline/ContentCreationPipeline.java:37`).
- Injection par **constructeur explicite** pour les classes API/service/noyau :
  `ContentController(ContentService contentService)` (`api/ContentController.java:25`),
  `ContentStatusService(ContentRepository)` (`service/ContentStatusService.java:16`).
- Injection par **Lombok `@RequiredArgsConstructor`** pour les classes domaine/pipeline/client :
  `PlanningService` (`pipeline/planner/PlanningService.java:15`), `AiCallExecutor`
  (`common/ai/AiCallExecutor.java:25`), `PptxRenderEngine` (`pipeline/renderer/PptxRenderEngine.java:32`).
- **Production conditionnelle** via `@Produces` (`client/GenerativeAiApiProducer.java:24-36`).
- Configuration : `@ConfigProperty(name=..., defaultValue=...)`
  (`client/GenerativeAiGateway.java:31`, `pipeline/generator/ContentGenerationService.java:28`).

**Exception — champs `@Inject`** (uniquement dans `GenerativeAiGateway` :
`@Inject GenerativeAiApi generativeAiApi`, `@Inject ObjectMapper objectMapper`
`client/GenerativeAiGateway.java:20-23`).

**Problématique** : `ContentService` crée `new ObjectMapper()` et `Executors.newFixedThreadPool(5)`
directement (`service/ContentService.java:45-47`) au lieu d'injecter un `ObjectMapper` CDI,
et gère un pool de threads maison.

---

## 4. Construction des objets

**Dominante — Lombok `@Builder`/`@Data`/`@NoArgsConstructor`/`@AllArgsConstructor`** sur
essentiellement toutes les classes de données (DTO, modèles, entités, objets de réponse) :
`ContentResponse` (`dto/response/ContentResponse.java:16-20`), `Zone`
(`model/Zone.java:11-15`), `PresentationPlan` (`pipeline/planner/model/PresentationPlan.java:11-15`).
Usage au point d'appel via `ClassName.builder()...build()` (ex. `LayoutAssignmentService.java:85-94`).

**Exception** : `ContentGenerationService.createFallbackContent` construit un `SlideContent`
via constructeur + `setContent` (`pipeline/generator/ContentGenerationService.java:95-105`) au
lieu du builder, et utilise des types concrets `HashMap`/`Map` pour le contenu.

---

## 5. DTOs et mapping

**Dominante** :
- DTOs séparés requête/réponse sous `dto/request` et `dto/response`
  (`dto/request/CreateContentRequest.java`, `dto/response/ContentResponse.java`).
- **snake_case** en JSON via `@JsonProperty` (Jackson) : `CreateContentRequest.java:25`, `Zone.java:27`.
- Mapping entité ⇄ DTO par **MapStruct** en mode CDI :
  `@Mapper(componentModel = "cdi")` (`mapper/ContentMapper.java:19-21`).
- Mapping JSON interne (colonnes `JSON` de la DB) via méthodes `@Named` dédiées dans le mapper
  (`mapper/ContentMapper.java:28-30,66-124`).

**Exception** : les DTOs de modèle de domaine sous `pipeline/*/model` ne sont pas des DTOs
d'API ; ce sont des POJO Lombok désérialisables par Jackson (ex. `PresentationPlan`).

---

## 6. Validation

**Deux niveaux** :
- **Bean Validation (Jakarta)** sur les DTOs d'entrée : `@NotNull` sur `CreateContentRequest`
  (`dto/request/CreateContentRequest.java:21-31`), activé dans le contrôleur par `@Valid`
  (`api/ContentController.java:30`).
- **Validation métier par classe `*Validator`** dans les stades du pipeline :
  `PlanValidator` (règles N1–N6, `pipeline/planner/PlanValidator.java:23-30`),
  `ContentValidator`, `LayoutAssignmentValidator` (dans `pipeline/assigner/...`).

---

## 7. Exceptions et réponses d'erreur

**Dominante (courant) :**
- **Mapper REST uniforme** : `ApiExceptionMapper` (`common/exception/ApiExceptionMapper.java`),
  un `@Provider` qui produit `{ "error": { "code", "message" } }` via le record `ApiError`.
- **`NotFoundException`** (`common/exception/NotFoundException.java`) → 404 ; `SecurityException`
  → 400 (signature invalide) ; `IllegalArgumentException` → 400 ; `IllegalStateException` → 409 ;
  le reste → 500 (`log.error` + `INTERNAL_ERROR`).
- **Exception de domaine** : `AIPipelineException extends RuntimeException`
  (`common/exception/AIPipelineException.java`), levée dans les planificateurs (ex.
  `pipeline/planner/PlanningService.java:53`) et validators (attendue → 500).
- Exceptions JDK standard pour cas métier : `IllegalArgumentException`
  (`service/ContentService.java:89`), `IllegalStateException`
  (`pipeline/assigner/LayoutAssignmentService.java:46`), `SecurityException`
  (`service/ContentService.java:93`).
- Les méthodes REST du contrôleur lèvent les exceptions et laissent le mapper produire la réponse
  (`api/ContentController.java`), sans JSON fabriqué à la main.

**Historique (à ne pas reproduire)** : l'ancien code du contrôleur assemblait le JSON d'erreur à
la main : `"{\"error\": \"" + e.getMessage() + "\"}"` — remplacé par l'`ApiExceptionMapper` (risque
d'injection/formatage sur les messages contenant des guillemets).

---

## 8. Logging

**Dominante (courant) — Lombok `@Slf4j` (SLF4J) partout** :
- `@Slf4j` sur les classes API/service/pipeline/storage/client/domaine : `ContentController`
  (`api/ContentController.java:16`), `ContentService` (`service/ContentService.java:27`),
  `PptxRenderEngine` (`pipeline/renderer/PptxRenderEngine.java:30`), `GenerativeAiGateway`
  (`client/GenerativeAiGateway.java:16`).

Niveaux utilisés : `log.debug/info/warn/error` et variants `log.error(..., e)` pour les
exceptions complètes (`ApiExceptionMapper.java:41`).

**Historique** : `ContentController`, `ContentService`, `ContentCreationPipeline`,
`MinioStorageAdapter` utilisaient `org.jboss.logging.Logger` — migrés vers `@Slf4j`. La convention
est désormais SLF4J `@Slf4j` partout.

---

## 9. Gestion de null

**Dominante — défensif, sans `@Nullable` obligatoire** :
- Tests `null`/`isBlank()`/`isEmpty()` avant parsing (ex. `pipeline/ContentCreationPipeline.java:127-133`,
  `mapper/ContentMapper.java:68-76`).
- `Optional` pour résultats optionnels d'assignation : `Optional<LayoutAssignmentResult>`
  (`pipeline/assigner/LayoutAssignmentService.java:100`), `Optional<SlideLayoutPart>`
  (`pipeline/renderer/SlideFactory.java:43`).
- Retour `null` pour « introuvable » dans certains services : `ContentService.getContent` renvoie
  `null` (contrôlé ensuite dans le contrôleur `api/ContentController.java:73`).
- `@Builder.Default` pour booléens : `webSearch = false`
  (`dto/request/CreateContentRequest.java:38-40`, `entity/Content.java:47-48`).

**Problématique** : mélange `Optional` / `null` / exception selon les classes ; pas de
convention unique claire.

---

## 10. Transactions

**Dominante — `@jakarta.transaction.Transactional` sur les méthodes d'écriture des services** :
`ContentService.createContent` (`service/ContentService.java:50`),
`ContentService.uploadDocument` (`:81`), `ContentStatusService.markRunning`/`markSucceeded`/`markFailed`
(`service/ContentStatusService.java:20,28,38`).

Sauvegarde via Panache : `contentRepository.save()` / `update()` (active persist/merge)
(`repository/ContentRepository.java:25-34`).

---

## 11. Commentaires et JavaDoc

**Dominante — commentaires/JavaDoc utiles, en anglais** :
- Commentaires de ligne expliquant l'intention : `// Step 1: Analyze template`
  (`pipeline/ContentCreationPipeline.java:118`), `// Get content from database`
  (`service/ContentService.java:84`).
- JavaDoc sur méthodes/classes avec `@param`/`@return`/`@throws` : `PptxRenderEngine`
  (`pipeline/renderer/PptxRenderEngine.java:39-47`), `SlideFactory`
  (`pipeline/renderer/SlideFactory.java:35-42`), `AiCallExecutor` (`common/ai/AiCallExecutor.java:14-20`).
- Tous les commentaires/JavaDoc du code de production sont désormais en **anglais** (convention
  appliquée et unifiée). Seuls les **textes de prompts IA** (text blocks) restent en français, car
  ils sont destinés au modèle (ex. `pipeline/analyzer/TemplateAnalyzer.java:491`, `PlanningPromptBuilder`).

---

## 12. Formatage

**Dominante — espacements/Zones habituels, indentation 4 espaces, retour à la ligne ~120 cols**.
Pas de fichier `.editorconfig` / formatage automatique explicite détecté dans le dépôt
(à vérifier). Usage de text blocks (`"""`) pour les prompts longs :
`pipeline/planner/PlanningPromptBuilder.java:20-35`, `client/helper/AIMockProvider.java:19-51`.

---

## Résumé : pratiques à ne pas reproduire

- `new ObjectMapper()` / pools de threads créés directement dans les services
  (`service/ContentService.java:45-47`).
- Champs publics + `@ConfigProperty` sans encapsulation (`client/GroqGenerativeAiApi.java:34-41`).
- Code hors package racine (`MinimalRenderer.java`, `PoiRealisticRenderer.java`) dans `src/main/java`.
- (Résolu) l'ancien JSON d'erreur assemblé à la main dans le contrôleur et la double convention
  de logging / langue FR-EN des commentaires — désormais `ApiExceptionMapper` + `@Slf4j` + unifié en anglais.
