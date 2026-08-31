# AGENTS.md

## Project status
V1 API structure implemented with Quarkus, MySQL, MinIO, and Flyway. Pipeline M1-M5 functional (orchestrated by `pipeline/ContentCreationPipeline`). No test classes exist in the working tree yet; `docs/TESTING.md` documents the intended 3-level test contract. Renderer E2E tests were disabled due to the docx4j JAXB namespace prefix mapper issue.

## What this project is
AI-driven PPTX presentation generator with REST API. Pipeline en 5 milestones:
- M1 TemplateAnalyzer: analyse PPTX → JSON structure
- M2 PresentationPlanner: génère plan de présentation via AI
- M3 LayoutAssigner: assigne layouts aux slides
- M4 AIContentGenerator: génère contenu via AI
- M5 PresentationRenderer: génère PPTX final

## Key tech decisions
- **Language:** Java 21
- **Build:** Maven (`mvn compile`, `mvn test`)
- **Framework:** Quarkus 3.8.1 (CDI, REST, JPA/Panache)
- **Database:** MySQL 8.0 with Flyway migrations
- **Storage:** MinIO (S3-compatible) for templates and results
- **PPTX library:** docx4j 11.5.14 (JAXB-MOXy variant)
- **Mapping:** MapStruct 1.5.5.Final for DTO/entity mapping (CDI componentModel)
- **AI Gateway:** GenerativeAiGateway with retry/backoff, supports OpenRouter and Groq
- **Pipeline prompts:** Planning, layout assignment and content generation follow the contracts of `ContentCreationAPI_Specs.md`
- **Unit system:** EMU (1 inch = 914400 EMU)
- **JSON serialization:** Jackson with `@JsonProperty` for snake_case
- **Logging:** Lombok `@Slf4j` (SLF4J) everywhere; comments/JavaDoc in English

## Commands
- `mvn compile` - compile source
- `mvn test` - run tests
- `mvn quarkus:dev` - Quarkus dev mode
- `docker compose up` - start MySQL + MinIO

## Project structure
```
src/main/java/com/pptxgenerator/
├── api/                              # REST Controllers
│   └── ContentController.java        # /contentCreation/v1/contents
├── service/                          # Business logic
│   ├── ContentService.java
│   └── ContentStatusService.java
├── pipeline/                         # Pipeline stages M1-M5 (orchestrated by ContentCreationPipeline)
│   ├── ContentCreationPipeline.java  # M1-M5 orchestrator
│   ├── analyzer/                     # M1 template analysis
│   ├── planner/                      # M2 planning (+ model/)
│   ├── assigner/                     # M3 layout assignment (+ ai/, model/)
│   ├── generator/                    # M4 content generation (+ model/)
│   └── renderer/                     # M5 PPTX rendering (+ model/)
├── common/
│   ├── ai/                           # AiCallExecutor, AiResponseParser, SystemPromptLibrary
│   └── exception/                    # AIPipelineException, ApiExceptionMapper, error DTOs
├── client/                           # AI Gateway (OpenRouter, Groq, mock) + dto/ + helper/
├── entity/                           # JPA/Panache entities (Content, Template)
├── dto/                              # API DTOs (request/, response/)
├── mapper/                           # MapStruct mappers (ContentMapper)
├── repository/                       # Panache repositories (ContentRepository, TemplateRepository)
├── model/                            # Domain models + enums/
├── config/                           # (currently empty)
└── storage/                          # StoragePort interface + MinioStorageAdapter
```

## Database schema
- **template**: id, name, description, file_type, file_url, file_size_bytes, file_name, template_analysis (JSON), created_at, updated_at
- **content**: id, operation, model_id, output_format, template_id, instructions, inputs (JSON), web_search, options (JSON), status, signature_send_document, signature_fetch_result, document_url, result_url, warnings (JSON), error_message, submitted_at, queued_at, started_at, ended_at

## API Endpoints
### Content Creation (implemented in `api/ContentController.java`)
- `POST /contentCreation/v1/contents` - Create content generation request
- `POST /contentCreation/v1/contents/{contentId}/document` - Upload template document
- `GET /contentCreation/v1/contents/{contentId}` - Get content status
- `GET /contentCreation/v1/contents/{contentId}/result` - Download result

> Note: the Template Library endpoints below are specified in `ContentCreationAPI_Specs.md`
> but **not yet implemented** (no `TemplateController` / `TemplateService` exist).

### Template Library (planned, not implemented)
- `POST /contentCreation/v1/templates` - Create template
- `GET /contentCreation/v1/templates` - List templates
- `GET /contentCreation/v1/templates/{templateId}` - Get template
- `DELETE /contentCreation/v1/templates/{templateId}` - Delete template
- `GET /contentCreation/v1/templates/{templateId}/file` - Download template file

## Configuration (application.properties)
```properties
# Database
quarkus.datasource.db-kind=mysql
quarkus.datasource.username=content_user
quarkus.datasource.password=password
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/content_creation

# Flyway
quarkus.flyway.migrate-at-start=true

# MinIO
minio.url=http://localhost:9000
minio.access-key=minioadmin
minio.secret-key=minioadmin

# AI
app.ai.mock=true
app.ai.provider=groq
groq.api.key=${GROQ_API_KEY:}
```

## Known issues
- **Docx4j JAXB namespace prefix mapper**: Renderer tests fail with "namespacePrefixMapper is null" error. This is a known issue with docx4j MOXy variant. Tests are disabled until resolved.
- **Flyway en mode dev**: Flyway est désactivé en mode dev (`%dev.quarkus.flyway.active=false`) car MySQL n'est pas toujours démarré. En production, Flyway s'exécute automatiquement au démarrage.
- **Validation de schéma Hibernate**: En mode dev sans MySQL, Hibernate affiche des warnings sur les tables manquantes. Ce n'est pas bloquant.

## Démarrage
```bash
# Mode développement (sans MySQL requis)
mvn quarkus:dev

# Production (MySQL + MinIO requis)
docker compose up -d
mvn quarkus:dev -Dquarkus.profile=prod
```

## Conventions
- Spec en français, code en anglais; comments and JavaDoc in English
- JSON: snake_case via `@JsonProperty`
- Logging: Lombok `@Slf4j` (SLF4J) everywhere
- Error responses: `ApiExceptionMapper` produces `{ "error": { "code", "message" } }`
- Semantic layout types: `TITLE_SLIDE`, `SECTION_HEADER`, `CONTENT`, `TWO_COLUMN`, `CONTENT_WITH_MEDIA`, `BLANK`, `CUSTOM`
- Slide types (plan): `title`, `outline`, `section_transition`, `content`
- Planning fields: `slide_number`, `slide_type`, `purpose`, `content_brief`, `detailed_context`, optional `section_number`
- Layout assignment: deterministic for `title`/`outline`/`section_transition`, AI-assisted for `content`
- Content generation: parallel per slide; zone keys follow `{zone_type}_{zone_id}`
- Layout variety: every assignment avoids the previous two layout IDs when alternatives exist
- Layout AI: content slides only, two attempts with a 30-second timeout, then smart fallback
- Zone types: `title`, `body`, `subtitle`, `picture`, `footer`, `center_title`, `table`, `chart`
- Zone importance: `HIGH`, `MEDIUM`, `LOW`
- AI responses parsed via AiResponseParser (tolerates markdown fences, mixed text)

## Testing
- Tests unitaires avec JUnit 5
- Mock AI par défaut (`app.ai.mock=true`)
- H2 in-memory database for tests
- 3 levels documented in `docs/TESTING.md`: unit, integration, E2E (renderer E2E tests disabled due to docx4j JAXB issue)
