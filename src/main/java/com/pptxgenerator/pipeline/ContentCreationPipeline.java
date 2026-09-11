package com.pptxgenerator.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pptxgenerator.pipeline.analyzer.TemplateAnalyzer;
import com.pptxgenerator.pipeline.assigner.LayoutAssignmentService;
import com.pptxgenerator.pipeline.assigner.model.PlanWithLayouts;
import com.pptxgenerator.dto.request.ContentOptions;
import com.pptxgenerator.dto.request.InputContent;
import com.pptxgenerator.entity.Content;
import com.pptxgenerator.pipeline.generator.ContentGenerationService;
import com.pptxgenerator.pipeline.generator.model.GeneratedContent;
import com.pptxgenerator.model.TemplateAnalysis;
import com.pptxgenerator.model.enums.Tone;
import com.pptxgenerator.pipeline.planner.PlanningService;
import com.pptxgenerator.pipeline.planner.model.PresentationPlan;
import com.pptxgenerator.pipeline.renderer.PptxRenderEngine;
import com.pptxgenerator.pipeline.renderer.model.RenderResult;
import com.pptxgenerator.repository.ContentRepository;
import com.pptxgenerator.repository.TemplateRepository;
import com.pptxgenerator.service.ContentStatusService;
import com.pptxgenerator.entity.Template;
import com.pptxgenerator.repository.TemplateRepository;
import com.pptxgenerator.service.s3.S3ContentTemplateStorage;
import io.quarkus.arc.Arc;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Slf4j
@ApplicationScoped
public class ContentCreationPipeline {
    
    private final ContentRepository contentRepository;
    private final TemplateRepository templateRepository;
    private final S3ContentTemplateStorage s3ContentTemplateStorage;
    private final ContentStatusService statusService;
    private final TemplateAnalyzer templateAnalyzer;
    private final PlanningService planningService;
    private final LayoutAssignmentService layoutAssignmentService;
    private final ContentGenerationService contentGenerationService;
    private final PptxRenderEngine pptxRenderEngine;
    private final ObjectMapper objectMapper;
    private final ObjectMapper debugObjectMapper;

    @ConfigProperty(name = "app.pipeline.debug-json", defaultValue = "false")
    boolean debugJsonEnabled;

    public ContentCreationPipeline(
                                  ContentRepository contentRepository,
                                  TemplateRepository templateRepository,
                                  S3ContentTemplateStorage s3ContentTemplateStorage,
                                  ContentStatusService statusService,
                                  TemplateAnalyzer templateAnalyzer,
                                  PlanningService planningService,
                                  LayoutAssignmentService layoutAssignmentService,
                                  ContentGenerationService contentGenerationService,
                                  PptxRenderEngine pptxRenderEngine,
                                  ObjectMapper objectMapper) {

        this.contentRepository = contentRepository;
        this.templateRepository = templateRepository;
        this.s3ContentTemplateStorage = s3ContentTemplateStorage;
        this.statusService = statusService;
        this.templateAnalyzer = templateAnalyzer;
        this.planningService = planningService;
        this.layoutAssignmentService = layoutAssignmentService;
        this.contentGenerationService = contentGenerationService;
        this.pptxRenderEngine = pptxRenderEngine;
        this.objectMapper = objectMapper;
        this.debugObjectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Runs the full pipeline asynchronously as a Mutiny {@link Uni}, aligned with the
     * target architecture: the caller subscribes and orchestrates post-processing
     * (upload, status update) with {@code onItem}/{@code onFailure} handlers.
     *
     * <p>The blocking pipeline (docx4j + AI calls) is executed on the Mutiny default
     * worker pool, never on the caller (event-loop) thread. The CDI request context
     * is activated for the worker thread so {@code @Transactional} status updates work.
     */
    /**
     * Runs the full pipeline asynchronously as a Mutiny {@link Uni}, aligned with the
     * real project: {@code Uni<PPTXPipelineResult>} carries the rendered bytes and the
     * caller (ContentCreationService) orchestrates post-processing (result upload,
     * status transition) with {@code onItem}/{@code onFailure} handlers.
     *
     * <p>The blocking pipeline (docx4j + AI calls) is executed on the Mutiny default
     * worker pool, never on the caller (event-loop) thread. The CDI request context
     * is activated for the worker thread so {@code @Transactional} helpers work.
     */
    public Uni<PPTXPipelineResult> executeAsync(String contentId) {
        return Uni.createFrom().item(() -> runWithRequestContext(contentId))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private PPTXPipelineResult runWithRequestContext(String contentId) {
        Arc.container().requestContext().activate();
        try {
            return executePipeline(contentId);
        } catch (Exception e) {
            log.error("Pipeline execution threw for content {}", contentId, e);
            throw new IllegalStateException(e);
        } finally {
            Arc.container().requestContext().terminate();
        }
    }
    
    public PPTXPipelineResult executePipeline(String contentId) throws Exception {
        log.info("Starting pipeline for content: {}", contentId);
        
        // Get content from database
        Content content = contentRepository.findByContentId(contentId);
        if (content == null) {
            throw new IllegalArgumentException("Content not found: " + contentId);
        }
        
        // Download template
        String templatePath = downloadTemplate(content);

        // User-requested model id (null -> provider default in AiCallExecutor)
        String modelId = content.getModelId();

        try {
            // Step 1: Analyze template
            log.info("Step 1: Analyzing template for content: {}", contentId);
            PresentationMLPackage pptx = PresentationMLPackage.load(new File(templatePath));
            TemplateAnalysis templateAnalysis = templateAnalyzer.analyze(pptx, modelId);
            writeDebugJson(contentId, "template_analysis.json", templateAnalysis);


            // Options shared by steps 2-4
            ContentOptions options = parseOptions(content.getOptions());
            int minSlides = options != null && options.getNumSlides() != null && options.getNumSlides().getMin() != null
                ? options.getNumSlides().getMin() : 8;
            int maxSlides = options != null && options.getNumSlides() != null && options.getNumSlides().getMax() != null
                ? options.getNumSlides().getMax() : 15;
            String language = options != null && options.getLanguage() != null ? options.getLanguage() : "fr";
            String tone = options != null && options.getTone() != null ? options.getTone().name() : Tone.PROFESSIONAL.name();
            boolean webSearch = Boolean.TRUE.equals(content.getWebSearch());

            // Step 2: Generate plan
            log.info("Step 2: Generating plan for content: {}", contentId);
            List<InputContent> inputs = parseInputs(content.getInputs());
            List<String> inputTexts = inputs.stream().map(InputContent::getText).toList();
            PresentationPlan plan = planningService.generatePlan(
                content.getInstructions(), inputTexts, minSlides, maxSlides, language, tone, modelId);
            writeDebugJson(contentId, "presentation_plan.json", plan);

            // Step 3: Assign layouts
            log.info("Step 3: Assigning layouts for content: {}", contentId);
            PlanWithLayouts planWithLayouts = layoutAssignmentService.assignLayouts(plan, templateAnalysis, modelId);
            writeDebugJson(contentId, "plan_with_layouts.json", planWithLayouts);

            // Step 4: Generate content
            log.info("Step 4: Generating content for content: {}", contentId);
            GeneratedContent generatedContent = contentGenerationService.generateContent(
                planWithLayouts, language, tone, webSearch, modelId);
            writeDebugJson(contentId, "generated_content.json", generatedContent);

            // Step 5: Render PPTX
            log.info("Step 5: Rendering PPTX for content: {}", contentId);
            String outputPath = "target/output_" + contentId + ".pptx";
            RenderResult renderResult = pptxRenderEngine.render(
                templatePath, templateAnalysis, generatedContent, outputPath);
            writeDebugJson(contentId, "render_result.json", renderResult);

            // Aligned with the real project: the pipeline RETURNS the rendered bytes;
            // the caller uploads them to S3 and updates the statuses in the Mutiny chain.
            log.info("Pipeline completed successfully for content: {}", contentId);
            byte[] pptxBytes = Files.readAllBytes(Path.of(renderResult.getOutputFile().getAbsolutePath()));

            // Clean up temp output file
            Files.deleteIfExists(Path.of(outputPath));
            return new PPTXPipelineResult(pptxBytes);
        } finally {
            // Clean up downloaded template
            Files.deleteIfExists(Path.of(templatePath));
        }
    }
    
    public void markAsFailed(String contentId, String errorMessage) {
        statusService.markFailed(contentId, errorMessage);
    }

    private String downloadTemplate(Content content) throws Exception {
        // Preferred resolution: the template registered at upload time (templateId ->
        // template.fileUrl = "templates/<sourceId>/<fileName>"). Legacy fallback:
        // documentUrl parsing for contents created before the registration.
        Template template = content.getTemplateId() != null
                ? templateRepository.findByTemplateId(content.getTemplateId()) : null;
        String key;
        if (template != null) {
            key = template.getFileUrl();
        } else if (content.getDocumentUrl() != null) {
            key = content.getDocumentUrl();
        } else {
            throw new IllegalArgumentException(
                "Content " + content.getId() + " has neither templateId nor documentUrl; no template to analyze");
        }

        // fileUrl format: "templates/<sourceId>/<fileName>" (single bucket, folders)
        String[] keyParts = key.split("/");
        if (!S3ContentTemplateStorage.EXPECTED_FOLDER.equals(keyParts[0])) {
            throw new IllegalArgumentException("Unsupported document location: " + key);
        }
        String sourceId = keyParts.length > 2 ? keyParts[1] : null;
        String templateKeyFileName = keyParts[keyParts.length - 1];
        String tempPath = "target/template_" + content.getId() + ".pptx";

        try (InputStream templateStream = s3ContentTemplateStorage.downloadAsInputStream(
                sourceId, templateKeyFileName)) {
            Files.copy(templateStream, Path.of(tempPath));
        }

        return tempPath;
    }

    private List<InputContent> parseInputs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<InputContent>>() {});
        } catch (Exception e) {
            log.warn("Cannot parse content inputs", e);
            return List.of();
        }
    }

    private void writeDebugJson(String contentId, String fileName, Object value) {
        if (!debugJsonEnabled) return;
        try {
            Path directory = Path.of("target", "pipeline-debug", contentId);
            Files.createDirectories(directory);
            Path output = directory.resolve(fileName);
            debugObjectMapper.writeValue(output.toFile(), value);
            log.info("Pipeline JSON snapshot written: {}", output.toAbsolutePath());
        } catch (Exception e) {
            log.warn("Could not write pipeline JSON snapshot {}", fileName, e);
        }
    }

    private ContentOptions parseOptions(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ContentOptions.class);
        } catch (Exception e) {
            log.warn("Cannot parse content options", e);
            return null;
        }
    }
}
