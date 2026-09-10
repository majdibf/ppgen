package com.pptxgenerator.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pptxgenerator.client.GenerativeAiApi;
import com.pptxgenerator.client.GenerativeAiService;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import com.pptxgenerator.common.ai.AiCallExecutor;
import com.pptxgenerator.common.ai.AiResponseParser;
import com.pptxgenerator.model.TemplateAnalysis;
import com.pptxgenerator.pipeline.analyzer.AnalyzerPromptBuilder;
import com.pptxgenerator.pipeline.analyzer.ZoneCapacityCalculator;
import com.pptxgenerator.pipeline.analyzer.InheritedGeometryResolver;
import com.pptxgenerator.pipeline.analyzer.StructuralElementsDetector;
import com.pptxgenerator.pipeline.analyzer.TemplateAnalyzer;
import com.pptxgenerator.pipeline.analyzer.ThemeExtractor;
import com.pptxgenerator.pipeline.assigner.AILayoutAssigner;
import com.pptxgenerator.pipeline.assigner.DeterministicLayoutAssigner;
import com.pptxgenerator.pipeline.assigner.FallbackAssignment;
import com.pptxgenerator.pipeline.assigner.LayoutAssignmentPromptBuilder;
import com.pptxgenerator.pipeline.assigner.LayoutAssignmentService;
import com.pptxgenerator.pipeline.assigner.LayoutAssignmentValidator;
import com.pptxgenerator.pipeline.assigner.model.PlanWithLayouts;
import com.pptxgenerator.pipeline.generator.ContentGenerationService;
import com.pptxgenerator.pipeline.generator.ContentPromptBuilder;
import com.pptxgenerator.pipeline.generator.ContentValidator;
import com.pptxgenerator.pipeline.generator.SlideContentGenerator;
import com.pptxgenerator.pipeline.generator.model.GeneratedContent;
import com.pptxgenerator.pipeline.planner.PlanValidator;
import com.pptxgenerator.pipeline.planner.PlanningPromptBuilder;
import com.pptxgenerator.pipeline.planner.PlanningService;
import com.pptxgenerator.pipeline.planner.model.PresentationPlan;
import com.pptxgenerator.pipeline.planner.model.SlideType;
import com.pptxgenerator.pipeline.renderer.OoxmlHelper;
import com.pptxgenerator.pipeline.renderer.PlaceholderMapper;
import com.pptxgenerator.pipeline.renderer.PptxRenderEngine;
import com.pptxgenerator.pipeline.renderer.SlideFactory;
import com.pptxgenerator.pipeline.renderer.model.RenderResult;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage-by-stage integration tests: each stage runs in isolation and consumes the
 * checkpoint (JSON) of the previous stage, persisted under target/pipeline-fixtures/.
 *
 * <p>Purpose: debug one stage without re-running the whole pipeline. Run the whole class
 * once to (re)generate all checkpoints, then iterate stage by stage, e.g.:
 *   mvn test -Dtest='StageIntegrationTest#stage5_render'
 * Delete target/pipeline-fixtures/ to invalidate a checkpoint after changing an
 * upstream stage (e.g. after a prompt change, delete plan.json).
 *
 * <p>All AI calls go through a scripted stub, so the test is free, offline and repeatable.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StageIntegrationTest {

    private static final Path FIXTURES = Path.of("target", "pipeline-fixtures");
    private static final Path TEMPLATE = Path.of("template_1.pptx");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    // ========================================================================
    // STAGE 1 — template analysis (AI enrichment stubbed empty: the analyzer
    // keeps deterministic default descriptions)
    // ========================================================================

    @Test
    @Order(1)
    void stage1_analyzeTemplate() throws Exception {
        TemplateAnalysis analysis = checkpoint("template_analysis.json", TemplateAnalysis.class,
                () -> {
                    PresentationMLPackage pptx = PresentationMLPackage.load(TEMPLATE.toFile());
                    return templateAnalyzer().analyze(pptx, null);
                });

        assertThat(analysis.getLayouts()).isNotEmpty();
        assertThat(analysis.getSlideDimensions()).isNotNull();
        // M1/M5 contract: every zone carries an identity usable by the renderer
        analysis.getLayouts().forEach(layout ->
                assertThat(layout.getZones()).allSatisfy(zone ->
                        assertThat(zone.getZoneId()).isNotNull()));
    }

    // ========================================================================
    // STAGE 2 — narrative plan (scripted plan)
    // ========================================================================

    @Test
    @Order(2)
    void stage2_generatePlan() throws Exception {
        PresentationPlan plan = checkpoint("plan.json", PresentationPlan.class,
                () -> planningService().generatePlan(
                        "Présentation sur la domination chinoise au tennis de table",
                        List.of(
                                "La Chine domine le tennis de table mondial depuis les années 1960.",
                                "Plus de 60% des médailles d'or olympiques de la discipline sont chinoises."),
                        6, 12, "fr", "PROFESSIONAL", null));

        assertThat(plan.getSlides()).isNotEmpty();
        assertThat(plan.getSlides().get(0).getSlideType()).isEqualTo(SlideType.TITLE);
    }

    // ========================================================================
    // STAGE 3 — layout assignment (consumes checkpoints of stages 1 + 2)
    // ========================================================================

    @Test
    @Order(3)
    void stage3_assignLayouts() throws Exception {
        PresentationPlan plan = loadFixture("plan.json", PresentationPlan.class);
        TemplateAnalysis analysis = loadFixture("template_analysis.json", TemplateAnalysis.class);
        assertThat(plan).as("run stage2 first (delete FIXTURES to reset)").isNotNull();
        assertThat(analysis).as("run stage1 first (delete FIXTURES to reset)").isNotNull();

        PlanWithLayouts planWithLayouts = checkpoint("plan_with_layouts.json", PlanWithLayouts.class,
                () -> layoutAssignmentService().assignLayouts(plan, analysis, null));

        assertThat(planWithLayouts.getSlides())
                .allSatisfy(slide -> assertThat(slide.getLayout()).isNotNull());
    }

    // ========================================================================
    // STAGE 4 — content generation (consumes checkpoint of stage 3)
    // ========================================================================

    @Test
    @Order(4)
    void stage4_generateContent() throws Exception {
        PlanWithLayouts planWithLayouts = loadFixture("plan_with_layouts.json", PlanWithLayouts.class);
        assertThat(planWithLayouts).as("run stage3 first (delete FIXTURES to reset)").isNotNull();

        GeneratedContent content = checkpoint("generated_content.json", GeneratedContent.class,
                () -> contentGenerationService().generateContent(
                        planWithLayouts, "fr", "PROFESSIONAL", false, null));

        assertThat(content.getGeneratedContent().getSlides())
                .allSatisfy(slide -> assertThat(slide.getContent()).isNotNull());
    }

    // ========================================================================
    // STAGE 5 — render (consumes template + checkpoints 1 + 4; costs nothing)
    // ========================================================================

    @Test
    @Order(5)
    void stage5_render() throws Exception {
        TemplateAnalysis analysis = loadFixture("template_analysis.json", TemplateAnalysis.class);
        GeneratedContent content = loadFixture("generated_content.json", GeneratedContent.class);
        assertThat(analysis).as("run stage1 first").isNotNull();
        assertThat(content).as("run stage4 first").isNotNull();

        Path output = FIXTURES.resolve("presentation.pptx");
        RenderResult result = pptxRenderEngine().render(
                TEMPLATE.toString(), analysis, content, output.toString());

        assertThat(result.getTotalSlides()).isPositive();
        assertThat(output).exists();
        // structural QA: the produced file must be a loadable OOXML package
        assertThat(PresentationMLPackage.load(output.toFile())).isNotNull();
    }

    // ========================================================================
    // CHECKPOINTING
    // ========================================================================

    private interface StageSupplier<T> {
        T get() throws Exception;
    }

    /** Loads the checkpoint if it exists, otherwise computes, persists and returns it. */
    private <T> T checkpoint(String name, Class<T> type, StageSupplier<T> producer) throws Exception {
        T existing = loadFixture(name, type);
        if (existing != null) {
            return existing;
        }
        T produced = producer.get();
        Files.createDirectories(FIXTURES);
        MAPPER.writeValue(FIXTURES.resolve(name).toFile(), produced);
        return produced;
    }

    private <T> T loadFixture(String name, Class<T> type) throws Exception {
        Path file = FIXTURES.resolve(name);
        return Files.exists(file) ? MAPPER.readValue(file.toFile(), type) : null;
    }

    // ========================================================================
    // WIRING (plain constructors, no CDI, no database, no network)
    // ========================================================================

    /**
     * AI stub scripted by request type: the analyzer receives an empty enrichment
     * payload, the planner a valid minimal plan, the layout assigner a valid layout
     * choice, the content generator an empty content map. Free, offline, deterministic.
     */
    private GenerativeAiService aiGateway() {
        GenerativeAiApi stub = request -> new TextResponseDto(
                List.of(new TextResponseDto.TextCandidate(scriptedResponse(request))));
        GenerativeAiService generativeAiService = new GenerativeAiService();
        generativeAiService.generativeAiApi = stub;
        return generativeAiService;
    }

    private String scriptedResponse(TextRequestDto request) {
        String prompt = (request.getUserPrompt() == null ? "" : request.getUserPrompt());
        if (prompt.contains("INSTRUCTIONS UTILISATEUR")) {
            return """
                {"presentation_plan": {
                   "title": "La domination chinoise au tennis de table",
                   "narrative_arc": "Couverture → transitions → contenu",
                   "total_slides": 6,
                   "slides": [
                     {"slide_number": 1, "slide_type": "title",
                      "purpose": "Accrocher",
                      "content_brief": "Titre et sous-titre de couverture",
                      "detailed_context": "60% des médailles d'or olympiques chinoises depuis 1988"},
                     {"slide_number": 2, "slide_type": "section_transition",
                      "purpose": "Ouvrir la partie histoire",
                      "content_brief": "Section 01 : Une histoire de domination",
                      "detailed_context": "Domination continue depuis les années 1960",
                      "section_number": 1, "section_title": "Une histoire de domination"},
                     {"slide_number": 3, "slide_type": "content",
                      "purpose": "Établir les faits",
                      "content_brief": "Chiffres de la domination",
                      "detailed_context": "60% des médailles d'or ; sport national depuis 1959"},
                     {"slide_number": 4, "slide_type": "content",
                      "purpose": "Détailler les chiffres",
                      "content_brief": "Palmarès olympique",
                      "detailed_context": "32 titres olympiques sur 53 possibles entre 1988 et 2021"},
                     {"slide_number": 5, "slide_type": "section_transition",
                      "purpose": "Ouvrir la partie technique",
                      "content_brief": "Section 02 : La fabrique de l'excellence",
                      "detailed_context": "Techniques et formation précoce",
                      "section_number": 2, "section_title": "La fabrique de l'excellence"},
                     {"slide_number": 6, "slide_type": "content",
                      "purpose": "Conclure la partie technique",
                      "content_brief": "Le modèle de formation",
                      "detailed_context": "Académies spécialisées, formation dès 6 ans"}
                   ]
                 }}""";
        }
        if (prompt.contains("layout_id")) {
            return "{\"layout_id\": \"layout_0\", \"rationale\": \"stub\"}";
        }
        if (prompt.contains("ZONES À REMPLIR")) {
            // SlideContent is a flat zone-key map; unknown keys are ignored by the renderer
            return "{\"title_0\": \"Contenu de démonstration\", \"body_1\": \"- Point 1\\n- Point 2\"}";
        }
        // analyzer enrichment calls
        return "{\"enriched_zones\": [], \"enriched_layouts\": []}";
    }

    private AiCallExecutor aiCallExecutor() {
        return new AiCallExecutor(aiGateway(), new AiResponseParser());
    }

    private TemplateAnalyzer templateAnalyzer() {
        return new TemplateAnalyzer(
                aiCallExecutor(),
                new ThemeExtractor(),
                new StructuralElementsDetector(),
                new ZoneCapacityCalculator(),
                new AnalyzerPromptBuilder(),
                new InheritedGeometryResolver());
    }

    private PlanningService planningService() {
        return new PlanningService(aiCallExecutor(), new PlanningPromptBuilder(), new PlanValidator());
    }

    private LayoutAssignmentService layoutAssignmentService() {
        return new LayoutAssignmentService(
                new DeterministicLayoutAssigner(),
                new AILayoutAssigner(aiCallExecutor(), new LayoutAssignmentPromptBuilder()),
                new FallbackAssignment(),
                new LayoutAssignmentValidator());
    }

    private ContentGenerationService contentGenerationService() {
        return new ContentGenerationService(
                new SlideContentGenerator(aiCallExecutor(), new ContentPromptBuilder()),
                new ContentValidator());
    }

    private PptxRenderEngine pptxRenderEngine() {
        return new PptxRenderEngine(new SlideFactory(), new PlaceholderMapper(new OoxmlHelper()));
    }
}
