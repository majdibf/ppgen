# Gestion des erreurs du pipeline asynchrone

> Document conceptuel (aucun code modifié). Décrit **comment** intercepter les erreurs
> du pipeline, **quelle exception** renvoyer, **quel code stocker** et **quoi exposer**
> via `GET /contentCreation/v1/contents/{contentId}`.

---

## 1. Modèle mental : deux chemins d'erreur distincts

Une erreur ne s'exprime pas de la même façon selon qu'elle survient *avant* ou *après*
le retour HTTP. Il faut bien séparer les deux.

| | **Erreur synchrone** | **Erreur asynchrone** |
|---|---|---|
| Quand | pendant la requête REST (avant le 200) | pendant l'exécution du pipeline (après le 200) |
| Où levée | `createContent`, `uploadDocument` (validation, signature, not-found) | `executePipeline` (stades M1-M5, upload, etc.) |
| Canal d'erreur | **`ApiExceptionMapper` → HTTP** (400/401/404/409) | **base de données** (`status = FAILED`, `error_message`) |
| Visible par le client | réponse de la requête en cours | relecture via `GET /contents/{id}` |

**Règle d'or :** une exception levée dans le thread asynchrone **ne peut plus produire
de code HTTP**. Le client a déjà reçu son 200 (`ContentService.uploadDocument` →
`return contentMapper.toResponse(content)`). Le seul moyen de signaler un échec
asynchrone est d'écrire dans la table `content`, puis de laisser le client relire l'état.

Conséquence sur l'existant : `ApiExceptionMapper` (`common/exception/ApiExceptionMapper.java`)
**ne doit pas être considéré comme le gestionnaire des erreurs du pipeline**. Il ne couvre
que le chemin synchrone. Aujourd'hui le pipeline est lancé depuis `uploadDocument` via
`executorService.submit(...)` et l'erreur est capturée localement par un `catch (Exception e)`
(`ContentService.java:119-125`) qui appelle `pipeline.markAsFailed(...)`.

---

## 2. Chaîne de bout en bout visée

```
Exception du pipeline
   │
   ▼
AIPipelineException (ou sous-type métier)  ← UN SEUL type racine, avec un code
   │   .getCode()      → code machine stable ("AI_TIMEOUT", "TEMPLATE_NO_LAYOUT", ...)
   │   .getMessage()   → message lisible (déjà en anglais, jamais null)
   ▼
Capteur asynchrone (ContentService / ContentCreationPipeline)
   │   #1 le catch n'affiche que exception.getCode() + getMessage()
   │   #2 appelle statusService.markFailed(id, code, message)
   ▼
Table content : columns error_code + error_message
   ▼
ContentMapper.mapError(entity)  →  ContentResponse.error { code, message }
   ▼
GET /contents/{contentId}  →  { "status": "FAILED", "error": { "code":"AI_TIMEOUT", "message":"..." } }
```

Trois champs à distinguer, souvent confondus :

- **code HTTP** : n'existe que pour le chemin synchrone ; sans objet après le 200.
- **code d'erreur métier** : chaîne stable, lisible par un client (ex. `AI_TIMEOUT`).
  → **à ajouter en base** (il n'existe pas encore) et **exposé** dans `ContentResponse.error.code`.
- **message** : détail humain, stocké dans `error_message`, exposé dans `ContentResponse.error.message`.

---

## 3. Quelle exception renvoyer

### 3.1 Une seule racine métier : `AIPipelineException`

Toutes les exceptions métier du pipeline doivent étendre (ou être) `AIPipelineException`
déjà présente (`common/exception/AIPipelineException.java`). Elle porte un **code**.

Aujourd'hui l'asynchrone aplatit tout en `e.getMessage()` : un `IllegalStateException`,
une `NullPointerException`, une `AIPipelineException`… perdent toute structure. En
standardisant sur `AIPipelineException`, le capteur asynchrone peut toujours extraire
`getCode()` (avec défaut si code manquant) et `getMessage()` (jamais null).

### 3.2 Sous-types / catégories recommandées

Plutôt qu'une centaine de codes, regrouper par nature d'erreur afin que le client
puisse décider d'une stratégie de relance :

| Catégorie | Code(s) proposés | Déclencheurs types | Relançable |
|---|---|---|---|
| Erreur de configuration / données | `TEMPLATE_NO_LAYOUT`, `TEMPLATE_INVALID`, `CONTENT_NOT_FOUND` | template sans layouts, analyse impossible | non |
| Erreur IA | `AI_TIMEOUT`, `AI_INVALID_RESPONSE`, `AI_UNAVAILABLE`, `AI_OUTPUT_TOO_LARGE` | échec d'appel IA, parse réponse invalide | oui |
| Erreur de contenu | `PLAN_VALIDATION_FAILED`, `OUTPUT_TOO_LARGE` / `SLIDE_SPLIT` | plan non validable malgré correctifs | partiel |
| Erreur de rendu | `RENDER_FAILED`, `SQL_DOCX4J_ERROR` | échec docx4j / génération PPTX | oui |
| Erreur interne / non catégorisée | `INTERNAL_ERROR` | bug non prévu, NPE | non |

Règle : un **même** code ne doit pas mélanger « config cassée » (non relançable) et
« souci transitoire » (relançable). C'est le seul attribut dont le client a réellement besoin.

### 3.3 Règle sur `getMessage()`

- Le message doit être **toujours non null** (éviter le `e.getMessage() == null` qui,
  aujourd'hui, écrit la chaîne `"null"` en base — ex. un NPE sur `slides` null du planner).
- Renseigné en **anglais** (convention projet), tourné vers l'exploitation.
- Ne jamais y mettre de secret (token, clé, URL signée).

Pour les exceptions « runtime » non prévues (NPE, `IllegalStateException`...), on ne
les force pas à devenir `AIPipelineException` systématiquement : le capteur les **enveloppe**
en `AIPipelineException(INTERNAL_ERROR, exception)` et extrait le message de façon sûre.

---

## 4. Quel code stocker en base

### 4.1 État actuel (constat)

- Table `content` : **une seule** colonne `error_message` (TEXT). **Pas de colonne `error_code`.**
- `ContentMapper.mapError` (`mapper/ContentMapper.java:56-64`) renvoie toujours
  `ErrorDetail{ code="PIPELINE_ERROR", message=errorMessage }`, quel que soit le problème.
- Donc aujourd'hui le **code est figé** et le message est tout ce qui distingue deux échecs.

### 4.2 État cible

- **Ajouter une colonne** `error_code VARCHAR(50) NULL` sur `content`
  (nouvelle migration Flyway `V1.2`, initialisée à null ; nulle tant que le contenu n'a pas échoué).
- `error_message` reste le détail humain.
- `markFailed` écrit **les deux** : `status = FAILED`, `error_code = code`, `error_message = message`.

### 4.3 Valeurs par défaut

- Si le code est absent/`INTERNAL_ERROR` → stocker `INTERNAL_ERROR` en `error_code`.
- Si le message est null → stocker une valeur neutre non null (ex. classe de l'exception)
  pour ne jamais écrire `"null"`.

---

## 5. Quoi renvoyer dans `GET /contents/{id}`

Le contrat de sortie est déjà largement en place (`dto/response/ContentResponse.java`) :

```json
{
  "contentId": "cnt_...",
  "status": "FAILED",
  "error": {
    "code": "AI_TIMEOUT",        // ← doit venir de error_code, plus "PIPELINE_ERROR" en dur
    "message": "..."             // ← error_message
  },
  "warnings": [ ... ],           // facultatif : retours de stade (variété, etc.)
  "submittedAt": "...",
  "queuedAt": "...",
  "startedAt": "...",
  "endedAt": "..."
}
```

### Changements requis sur `mapError`

- Lire le **code depuis `error_code`** au lieu du `"PIPELINE_ERROR"` codé en dur.
- Fallback : si `error_code` est null mais `status == FAILED` → `INTERNAL_ERROR`.
- Ne renvoyer `error` que si `status == FAILED` (ou code non null), sinon `error: null`.

### Règle de cohérence : `status` et `error`

- `error` n'est **jamais renseigné** si `status != FAILED`. Un contenu `QUEUED`/`RUNNING`/`SUCCEEDED`
  ne doit pas exposer d'erreur.
- À l'inverse, `status == FAILED` implique `error` non null (code + message).

---

## 6. Où intercepter (points de capture)

| Point d'interception | Rôle | Action |
|---|---|---|
| `ContentService.uploadDocument` (thread REST) | lance le pipeline **fait** | capture locale, `markFailed(code, message)` — **doit devenir la seule** aujourd'hui utilisée |
| `ContentExecutionPipeline.processAsync` | variante Uni alternative | capte aussi → `markFailed(...)` ; à harmoniser avec le point ci-dessus ou à supprimer (double source) |
| `ContentStatusService.markFailed` | écriture DB | **à étendre** : accepter `code` + `message` et persister `error_code` |
| `ApiExceptionMapper` | erreurs **synchrones** uniquement | **ne pas** l'utiliser pour l'asynchrone ; utile encore pour create/upload |

Note : aujourd'hui **deux** mécanismes coexistent (`uploadDocument`→`markAsFailed` et
`processAsync`→`markFailed`). Il faut **ne garder qu'un seul canal d'écriture d'échec**
pour éviter des incohérences (qui écrit en dernier, avec quel code).

---

## 7. Anti-patterns à éviter

1. **Retourner un code HTTP depuis le pipeline asynchrone** — impossible, et trompeur.
2. **Dépendre de `ApiExceptionMapper` pour l'asynchrone** — il ne s'exécute que dans le
   thread REST synchrones.
3. **Stocker seulement `e.getMessage()`** — aucune catégorie exploitable, risque de `"null"`.
4. **Mettre le code en dur dans le mapper** (`"PIPELINE_ERROR"`) — tous les échecs se
   ressemblent.
5. **Mixer « non relançable » et « relançable » sous un même code** — le client ne peut
   pas décider de retenter.

---

## 8. Questions ouvertes / décisions à trancher

- **Granularité des codes** : combien en vouloir ? (chaque cause distincte vs ~8 catégories).
- **Ajouter `retry_count` / « relançable »** dans la réponse, ou laisser le client décider
  depuis le code seul ?
- **Warnings async** : faut-il aussi les persister (`content.warnings` existe déjà en JSON)
  pour les exposer au GET final (troncature, variété limitée...) ?
- **Harmoniser** `processAsync` vs `uploadDocument` : lesquels garder, lesquels supprimer ?
- **`markSucceeded`** écrit-il aussi un `error_code = null` pour purger les anciennes erreurs
  en cas de relance ?

---

## 9. Récapitulatif des changements suggérés (pour validation)

| Couche | Changement |
|---|---|
| DB (Flyway) | nouvelle migration : colonne `content.error_code VARCHAR(50)` |
| `AIPipelineException` | garantir `code` + `message` non null ; envelopper les runtime |
| `ContentStatusService.markFailed` | accepter `code` + `message`, persister `error_code` |
| `ContentService` / pipeline | un seul canal de capture ; extraire code+message ; jamais `"null"` |
| `ContentMapper.mapError` | lire `error_code`, fallback `INTERNAL_ERROR`, ne renvoyer que si `FAILED` |
| `ApiExceptionMapper` | inchangé (chemin synchrone uniquement) |

Ce document est **conceptuel** : il ne décrit pas encore l'implémentation définitive.
Une validation de ces choix (notamment la granularité des codes et l'harmonisation des
deux canaux de capture) est recommandée avant d'écrire la migration et le code.
