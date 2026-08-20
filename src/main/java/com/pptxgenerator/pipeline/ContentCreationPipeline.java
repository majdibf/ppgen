package com.pptxgenerator.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pptxgenerator.analyzer.TemplateAnalysisService;
import com.pptxgenerator.assigner.LayoutAssignmentService;
import com.pptxgenerator.assigner.model.PlanWithLayouts;
import com.pptxgenerator.dto.request.ContentOptions;
import com.pptxgenerator.dto.request.InputContent;
import com.pptxgenerator.entity.Content;
import com.pptxgenerator.generator.ContentGenerationService;
import com.pptxgenerator.generator.model.GeneratedContent;
import com.pptxgenerator.model.TemplateAnalysis;
import com.pptxgenerator.model.enums.Tone;
import com.pptxgenerator.planner.PlanningService;
import com.pptxgenerator.planner.model.PresentationPlan;
import com.pptxgenerator.renderer.PptxRenderEngine;
import com.pptxgenerator.renderer.model.RenderResult;
import com.pptxgenerator.repository.ContentRepository;
import com.pptxgenerator.service.ContentStatusService;
import com.pptxgenerator.storage.StorageService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class ContentCreationPipeline {
    
    private static final Logger LOG = Logger.getLogger(ContentCreationPipeline.class);
    

    private final ContentRepository contentRepository;
    private final StorageService storageService;
    private final ContentStatusService statusService;
    private final TemplateAnalysisService templateAnalysisService;
    private final PlanningService planningService;
    private final LayoutAssignmentService layoutAssignmentService;
    private final ContentGenerationService contentGenerationService;
    private final PptxRenderEngine pptxRenderEngine;
    private final ObjectMapper debugObjectMapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    @ConfigProperty(name = "app.pipeline.debug-json", defaultValue = "true")
    boolean debugJsonEnabled;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContentCreationPipeline(
                                  ContentRepository contentRepository,
                                  StorageService storageService,
                                  ContentStatusService statusService,
                                  TemplateAnalysisService templateAnalysisService,
                                  PlanningService planningService,
                                  LayoutAssignmentService layoutAssignmentService,
                                  ContentGenerationService contentGenerationService,
                                  PptxRenderEngine pptxRenderEngine) {

        this.contentRepository = contentRepository;
        this.storageService = storageService;
        this.statusService = statusService;
        this.templateAnalysisService = templateAnalysisService;
        this.planningService = planningService;
        this.layoutAssignmentService = layoutAssignmentService;
        this.contentGenerationService = contentGenerationService;
        this.pptxRenderEngine = pptxRenderEngine;
    }
    
    public Uni<Void> processAsync(String contentId) {
        return Uni.createFrom().voidItem()
            .onItem().transformToUni(v -> {
                try {
                    executePipeline(contentId);
                    return Uni.createFrom().voidItem();
                } catch (Exception e) {
                    LOG.errorf("Pipeline failed for content %s: %s", contentId, e.getMessage());
                    
                    // Update content with error
                    try {
                        Content content = contentRepository.findByContentId(contentId);
                        if (content != null) {
                            statusService.markFailed(contentId, e.getMessage());
                        }
                    } catch (Exception ex) {
                        LOG.errorf("Failed to update error status: %s", ex.getMessage());
                    }
                    
                    return Uni.createFrom().failure(e);
                }
            });
    }
    
    public void executePipeline(String contentId) throws Exception {
        LOG.infof("Starting pipeline for content: %s", contentId);
        
        // Get content from database
        Content content = contentRepository.findByContentId(contentId);
        if (content == null) {
            throw new IllegalArgumentException("Content not found: " + contentId);
        }
        
        // Update status to RUNNING
        statusService.markRunning(contentId);
        
        // Download template
        String templatePath = downloadTemplate(content);
        
        try {
            // Step 1: Analyze template
            LOG.infof("Step 1: Analyzing template for content: %s", contentId);
            PresentationMLPackage pptx = PresentationMLPackage.load(new File(templatePath));
            TemplateAnalysis templateAnalysis = templateAnalysisService.analyze(pptx);
            writeDebugJson(contentId, "template_analysis.json", templateAnalysis);


            // Options communes aux étapes 2-4
            ContentOptions options = parseOptions(content.getOptions());
            int minSlides = options != null && options.getNumSlides() != null && options.getNumSlides().getMin() != null
                ? options.getNumSlides().getMin() : 8;
            int maxSlides = options != null && options.getNumSlides() != null && options.getNumSlides().getMax() != null
                ? options.getNumSlides().getMax() : 15;
            String language = options != null && options.getLanguage() != null ? options.getLanguage() : "fr";
            String tone = options != null && options.getTone() != null ? options.getTone().name() : Tone.PROFESSIONAL.name();
            boolean webSearch = Boolean.TRUE.equals(content.getWebSearch());

            // Step 2: Generate plan
            LOG.infof("Step 2: Generating plan for content: %s", contentId);
            List<InputContent> inputs = parseInputs(content.getInputs());
            List<String> inputTexts = inputs.stream().map(InputContent::getText).toList();
            PresentationPlan plan = planningService.generatePlan(
                content.getInstructions(), inputTexts, minSlides, maxSlides, language, tone);
            writeDebugJson(contentId, "presentation_plan.json", plan);

            // Step 3: Assign layouts
            LOG.infof("Step 3: Assigning layouts for content: %s", contentId);
            PlanWithLayouts planWithLayouts = layoutAssignmentService.assignLayouts(plan, templateAnalysis);
            writeDebugJson(contentId, "plan_with_layouts.json", planWithLayouts);

            // Step 4: Generate content
            LOG.infof("Step 4: Generating content for content: %s", contentId);
            GeneratedContent generatedContent = contentGenerationService.generateContent(
                planWithLayouts, language, tone, webSearch);
            writeDebugJson(contentId, "generated_content.json", generatedContent);

            // Step 5: Render PPTX
            LOG.infof("Step 5: Rendering PPTX for content: %s", contentId);
            String outputPath = "target/output_" + contentId + ".pptx";
            RenderResult renderResult = pptxRenderEngine.render(
                templatePath, templateAnalysis, planWithLayouts, generatedContent, outputPath);
            writeDebugJson(contentId, "render_result.json", renderResult);

            // Upload result
            String outputFile = renderResult.getOutputFile().getAbsolutePath();
            try (InputStream resultStream = new FileInputStream(outputFile)) {
                String resultKey = "results/" + contentId + "/presentation.pptx";
                storageService.uploadResult(resultKey, resultStream,
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation");

                // Update content with result
                statusService.markSucceeded(contentId, storageService.getResultUrl(resultKey));

                LOG.infof("Pipeline completed successfully for content: %s", contentId);
            }

            // Clean up temp file
            Files.deleteIfExists(Path.of(outputPath));

        } finally {
            // Clean up downloaded template
            Files.deleteIfExists(Path.of(templatePath));
        }
    }
    
    public void markAsFailed(String contentId, String errorMessage) {
        statusService.markFailed(contentId, errorMessage);
    }
    
    public void updateContentStatus(Content content, com.pptxgenerator.model.enums.ContentStatus status, 
                                    Instant startedAt, String errorMessage) {
        content.setStatus(status);
        if (startedAt != null) {
            content.setStartedAt(startedAt);
        }
        if (errorMessage != null) {
            content.setErrorMessage(errorMessage);
        }
        contentRepository.update(content);
    }
    
    private String downloadTemplate(Content content) throws Exception {
        String templateKey = content.getDocumentUrl().substring(content.getDocumentUrl().indexOf("/") + 1);
        String tempPath = "target/template_" + content.getId() + ".pptx";
        
        try (InputStream templateStream = storageService.downloadTemplate(templateKey)) {
            Files.copy(templateStream, Path.of(tempPath));
        }
        
        return tempPath;
    }

    private List<InputContent> parseInputs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<InputContent>>() {});
        } catch (Exception e) {
            LOG.warnf("Cannot parse content inputs: %s", e.getMessage());
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
            LOG.infof("Pipeline JSON snapshot written: %s", output.toAbsolutePath());
        } catch (Exception e) {
            LOG.warnf("Could not write pipeline JSON snapshot %s: %s", fileName, e.getMessage());
        }
    }

    private ContentOptions parseOptions(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ContentOptions.class);
        } catch (Exception e) {
            LOG.warnf("Cannot parse content options: %s", e.getMessage());
            return null;
        }
    }
}
