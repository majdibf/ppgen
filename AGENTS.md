# AGENTS.md

## Project status
M1 (TemplateAnalyzer), M2 (PresentationPlanner), M3 (LayoutAssigner), M4 (AIContentGenerator) et M5 (PresentationRenderer) implémentés. Build system: Maven avec Quarkus. Source code dans `src/main/java/com/pptxgenerator/`. Tests passent contre `template_1.pptx`.

## What this project is
AI-driven PPTX presentation generator in Java. Pipeline en 5 milestones:
- M1 TemplateAnalyzer: analyse PPTX → JSON structure
- M2 PresentationPlanner: génère plan de présentation via AI
- M3 LayoutAssigner: assigne layouts aux slides
- M4 AIContentGenerator: génère contenu via AI
- M5 PresentationRenderer: génère PPTX final

## Key tech decisions
- **Language:** Java 21
- **Build:** Maven (`mvn compile`, `mvn test`)
- **Framework:** Quarkus 3.8.1 (CDI, configuration)
- **PPTX library:** docx4j 11.5.14 (JAXB-MOXy variant)
- **AI Gateway:** GenerativeAiGateway avec retry/backoff, supporte OpenRouter et Groq
- **Unit system:** EMU (1 inch = 914400 EMU)
- **JSON serialization:** Jackson avec `@JsonProperty` pour snake_case
- **Infra:** MySQL 8.0 + MinIO via `docker-compose.yml`

## Commands
- `mvn compile` - compile source
- `mvn test` - run tests (utilise template_1.pptx)
- `mvn quarkus:dev` - mode développement Quarkus
- `docker compose up` - start MySQL + MinIO

## Project structure
```
src/main/java/com/pptxgenerator/
├── analyzer/
│   └── TemplateAnalyzer.java       # M1: analyse PPTX → JSON
├── planner/
│   ├── PresentationPlanner.java    # M2: génère plan via AI
│   ├── LayoutAssigner.java         # M3: assigne layouts aux slides
│   ├── AIContentGenerator.java     # M4: génère contenu via AI
│   └── AiResponseParser.java       # parser JSON tolerant
├── renderer/
│   └── PresentationRenderer.java   # M5: génère PPTX final
├── client/                          # AI Gateway
│   ├── GenerativeAiGateway.java    # point d'entrée avec retry
│   ├── GenerativeAiApi.java        # interface
│   ├── OpenRouterGenerativeAiApi.java
│   ├── GroqGenerativeAiApi.java
│   ├── GenerativeAiApiProducer.java # factory CDI
│   ├── builder/GenerativeAiRequestBuilder.java
│   ├── dto/TextRequestDto.java, TextResponseDto.java, JsonSchemaDto.java
│   └── helper/AIMockProvider.java, AIRequestType.java
└── model/                           # POJOs JSON schema
    ├── TemplateStructure.java, SlideLayout.java, Zone.java, etc.  # M1
    ├── PresentationPlan.java, PlanSlide.java                       # M2
    ├── EnrichedPlan.java, EnrichedSlide.java                       # M3
    ├── ContentMap.java, SlideContent.java, ZoneContent.java,       # M4
    │   ChartData.java
    └── (M5 utilise les modèles existants)
```

## Configuration (application.properties)
```properties
app.ai.mock=true                    # true = mock, false = real AI
app.ai.provider=openrouter          # openrouter ou groq
openrouter.api.key=${OPENROUTER_API_KEY:}
groq.api.key=${GROQ_API_KEY:}
```

## AI Gateway usage
```java
@Inject GenerativeAiGateway aiGateway;

TextRequestDto request = GenerativeAiRequestBuilder.builder()
    .systemPrompt("Tu es...")
    .userPrompt("Génère...")
    .outputSchema(jsonSchema)
    .temperature(0.7)
    .build()
    .toRequest();

TextResponseDto response = aiGateway.processRequest(request);
String text = response.getCandidates().get(0).getText();
```

## Pipeline complet M1 → M2 → M3 → M4 → M5
```java
// M1: Analyser le template
TemplateAnalyzer analyzer = new TemplateAnalyzer();
TemplateStructure template = analyzer.analyze("template_1.pptx");

// M2: Générer le plan
PresentationPlanner planner = new PresentationPlanner();
PresentationPlan plan = planner.generatePlan("Sujet de la présentation", template);

// M3: Assigner les layouts
LayoutAssigner assigner = new LayoutAssigner();
EnrichedPlan enrichedPlan = assigner.assignLayouts(template, plan);

// M4: Générer le contenu
AIContentGenerator contentGenerator = new AIContentGenerator();
ContentMap contentMap = contentGenerator.generateContent(enrichedPlan, "Sujet");

// M5: Générer le PPTX final
PresentationRenderer renderer = new PresentationRenderer();
File output = renderer.render(template, enrichedPlan, contentMap, "output.pptx");
```

## Conventions
- Spec en français, code en anglais
- JSON: snake_case via `@JsonProperty`
- Semantic layout types: `TITLE_SLIDE`, `SECTION_HEADER`, `CONTENT`
- Slide types (plan): `cover`, `section`, `content`, `conclusion`
- Zone types: `title`, `body`, `subtitle`, `picture`, `footer`, `center_title`, `table`, `chart`
- Zone importance: `HIGH`, `MEDIUM`, `LOW`
- AI responses parsées avec AiResponseParser (tolère markdown fences, texte mixte)

## Testing
- Tests unitaires avec JUnit 5
- Mock AI par défaut (`app.ai.mock=true`)
- Tests M1: TemplateAnalyzerTest
- Tests M2: AiResponseParserTest, PresentationPlannerTest
- Tests M3: LayoutAssignerTest
- Tests M4: AIContentGeneratorTest
- Tests M5: PresentationRendererTest

## Limitations M5
Le PresentationRenderer actuel est une implémentation de base. Les fonctionnalités suivantes nécessitent un développement supplémentaire:
- Rendu complet du texte dans les placeholders (nécessite manipulation avancée des shapes docx4j)
- Insertion d'images (nécessite gestion des relations et des parties d'image)
- Création de tableaux (nécessite création de shapes de type table)
- Création de graphiques (nécessite création de shapes de type chart)

Le renderer gère correctement:
- Chargement du template
- Association des layouts aux slides
- Création des slides dans le PPTX
- Sauvegarde du fichier final
