package com.pptxgenerator.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptxgenerator.dto.request.ContentOptions;
import com.pptxgenerator.dto.request.InputContent;
import com.pptxgenerator.entity.Content;
import com.pptxgenerator.model.ContentMap;
import com.pptxgenerator.model.EnrichedPlan;
import com.pptxgenerator.model.PresentationPlan;
import com.pptxgenerator.model.TemplateStructure;
import com.pptxgenerator.planner.AIContentGenerator;
import com.pptxgenerator.planner.LayoutAssigner;
import com.pptxgenerator.planner.PresentationPlanner;
import com.pptxgenerator.analyzer.TemplateAnalyzer;
import com.pptxgenerator.renderer.PresentationRenderer;
import com.pptxgenerator.repository.ContentRepository;
import com.pptxgenerator.storage.StorageService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

@ApplicationScoped
public class ContentCreationPipeline {
    
    private static final Logger LOG = Logger.getLogger(ContentCreationPipeline.class);
    
    private final PresentationPlanner presentationPlanner;
    private final LayoutAssigner layoutAssigner;
    private final AIContentGenerator aiContentGenerator;
    private final TemplateAnalyzer templateAnalyzer;
    private final PresentationRenderer presentationRenderer;
    private final ContentRepository contentRepository;
    private final StorageService storageService;
    private final ObjectMapper debugObjectMapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    @ConfigProperty(name = "app.pipeline.debug-json", defaultValue = "true")
    boolean debugJsonEnabled;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public ContentCreationPipeline(PresentationPlanner presentationPlanner,
                                  LayoutAssigner layoutAssigner,
                                  AIContentGenerator aiContentGenerator,
                                  TemplateAnalyzer templateAnalyzer,
                                  PresentationRenderer presentationRenderer,
                                  ContentRepository contentRepository,
                                  StorageService storageService) {
        this.presentationPlanner = presentationPlanner;
        this.layoutAssigner = layoutAssigner;
        this.aiContentGenerator = aiContentGenerator;
        this.templateAnalyzer = templateAnalyzer;
        this.presentationRenderer = presentationRenderer;
        this.contentRepository = contentRepository;
        this.storageService = storageService;
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
                            content.setStatus(com.pptxgenerator.model.enums.ContentStatus.FAILED);
                            content.setErrorMessage(e.getMessage());
                            content.setEndedAt(Instant.now());
                            contentRepository.update(content);
                        }
                    } catch (Exception ex) {
                        LOG.errorf("Failed to update error status: %s", ex.getMessage());
                    }
                    
                    return Uni.createFrom().failure(e);
                }
            });
    }
    
    @Transactional
    public void executePipeline(String contentId) throws Exception {
        LOG.infof("Starting pipeline for content: %s", contentId);
        
        // Get content from database
        Content content = contentRepository.findByContentId(contentId);
        if (content == null) {
            throw new IllegalArgumentException("Content not found: " + contentId);
        }
        
        // Update status to RUNNING
        updateContentStatus(content, com.pptxgenerator.model.enums.ContentStatus.RUNNING, Instant.now(), null);
        
        // Download template
        String templatePath = downloadTemplate(content);
        
        try {
            // Step 1: Analyze template
            LOG.infof("Step 1: Analyzing template for content: %s", contentId);
            TemplateStructure template = templateAnalyzer.analyze(templatePath);
            
            // Step 2: Generate plan
            LOG.infof("Step 2: Generating plan for content: %s", contentId);
            String topic = content.getInstructions() != null ? 
                content.getInstructions() : "Presentation";
            List<InputContent> inputs = parseInputs(content.getInputs());
            ContentOptions options = parseOptions(content.getOptions());
            PresentationPlan plan = presentationPlanner.generatePlan(topic, inputs, options, template);
            writeDebugJson(contentId, "01-plan.json", plan);
            
            // Step 3: Assign layouts
            LOG.infof("Step 3: Assigning layouts for content: %s", contentId);
            EnrichedPlan enrichedPlan = layoutAssigner.assignLayouts(template, plan);
            writeDebugJson(contentId, "02-plan-with-layouts.json", enrichedPlan);
            
            // Step 4: Generate content
            LOG.infof("Step 4: Generating content for content: %s", contentId);
            ContentMap contentMap = aiContentGenerator.generateContent(enrichedPlan, topic);
            writeDebugJson(contentId, "03-content-map.json", contentMap);
            
            // Step 5: Render PPTX
            LOG.infof("Step 5: Rendering PPTX for content: %s", contentId);
            String outputPath = "target/output_" + contentId + ".pptx";
            File outputFile = presentationRenderer.render(templatePath, template, enrichedPlan, contentMap, outputPath);
            
            // Upload result
            try (InputStream resultStream = new FileInputStream(outputFile)) {
                String resultKey = "results/" + contentId + "/presentation.pptx";
                storageService.uploadResult(resultKey, resultStream, 
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation");
                
                // Update content with result
                content.setResultUrl(storageService.getResultUrl(resultKey));
                content.setStatus(com.pptxgenerator.model.enums.ContentStatus.SUCCEEDED);
                content.setEndedAt(Instant.now());
                content.setSignatureFetchResult("sig_fr_" + UUID.randomUUID().toString().replace("-", ""));
                contentRepository.update(content);
                
                LOG.infof("Pipeline completed successfully for content: %s", contentId);
            }
            
            // Clean up temp file
            Files.deleteIfExists(Path.of(outputPath));
            
        } finally {
            // Clean up downloaded template
            Files.deleteIfExists(Path.of(templatePath));
        }
    }
    
    @Transactional
    public void markAsFailed(String contentId, String errorMessage) {
        try {
            Content content = contentRepository.findByContentId(contentId);
            if (content != null) {
                content.setStatus(com.pptxgenerator.model.enums.ContentStatus.FAILED);
                content.setErrorMessage(errorMessage);
                content.setEndedAt(Instant.now());
                contentRepository.update(content);
            }
        } catch (Exception e) {
            LOG.errorf("Failed to mark content as failed: %s", e.getMessage());
        }
    }
    
    @Transactional
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
