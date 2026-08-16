package com.pptxgenerator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptxgenerator.dto.request.CreateContentRequest;
import com.pptxgenerator.dto.response.ContentResponse;
import com.pptxgenerator.entity.Content;
import com.pptxgenerator.mapper.ContentMapper;
import com.pptxgenerator.model.enums.ContentStatus;
import com.pptxgenerator.pipeline.ContentCreationPipeline;
import com.pptxgenerator.repository.ContentRepository;
import com.pptxgenerator.storage.StorageService;
import io.quarkus.arc.Arc;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApplicationScoped
public class ContentService {
    
    private static final Logger LOG = Logger.getLogger(ContentService.class);
    
    private final ContentRepository contentRepository;
    private final ContentMapper contentMapper;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;
    private final ContentCreationPipeline pipeline;
    private final ExecutorService executorService;
    
    public ContentService(ContentRepository contentRepository, 
                         ContentMapper contentMapper,
                         StorageService storageService,
                         ContentCreationPipeline pipeline) {
        this.contentRepository = contentRepository;
        this.contentMapper = contentMapper;
        this.storageService = storageService;
        this.objectMapper = new ObjectMapper();
        this.pipeline = pipeline;
        this.executorService = Executors.newFixedThreadPool(5);
    }
    
    @Transactional
    public ContentResponse createContent(CreateContentRequest request) throws Exception {
        LOG.infof("Creating content with operation: %s", request.getOperation());
        
        // Generate content ID
        String contentId = "cnt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        
        // Determine initial status
        ContentStatus initialStatus = determineInitialStatus(request);
        
        // Generate signature for document upload if needed
        String sendDocumentSignature = null;
        if (initialStatus == ContentStatus.WAITING_DOCUMENT) {
            sendDocumentSignature = "sig_sd_" + UUID.randomUUID().toString().replace("-", "");
        }
        
        // Create content entity
        Content content = contentMapper.toEntity(request);
        content.setId(contentId);
        content.setStatus(initialStatus);
        content.setSignatureSendDocument(sendDocumentSignature);
        content.setSubmittedAt(Instant.now());
        
        // Save to database
        content = contentRepository.save(content);
        
        LOG.infof("Content created: %s with status: %s", contentId, initialStatus);
        
        return contentMapper.toResponse(content);
    }
    
    @Transactional
    public ContentResponse uploadDocument(String contentId, String signature, FileUpload file) throws Exception {
        LOG.infof("Uploading document for content: %s", contentId);
        
        // Get content from database
        Content content = contentRepository.findByContentId(contentId);
        if (content == null) {
            throw new IllegalArgumentException("Content not found: " + contentId);
        }
        
        // Validate signature
        if (!signature.equals(content.getSignatureSendDocument())) {
            throw new SecurityException("Invalid signature");
        }
        
        // Upload to storage
        String objectKey = "documents/" + contentId + "/" + file.fileName();
        try (InputStream inputStream = Files.newInputStream(file.filePath())) {
            storageService.uploadTemplate(objectKey, inputStream, file.contentType());
        }
        
        // Update content
        content.setDocumentUrl(storageService.getTemplateUrl(objectKey));
        content.setStatus(ContentStatus.QUEUED);
        content.setQueuedAt(Instant.now());
        
        content = contentRepository.update(content);
        
        LOG.infof("Document uploaded for content: %s, triggering async pipeline", contentId);
        
        // Trigger async pipeline processing in a separate thread with CDI context
        final String finalContentId = contentId;
        executorService.submit(() -> {
            // Activate CDI request context for this thread
            Arc.container().requestContext().activate();
            try {
                LOG.infof("Starting pipeline execution for content: %s", finalContentId);
                pipeline.executePipeline(finalContentId);
                LOG.infof("Pipeline completed successfully for content: %s", finalContentId);
            } catch (Exception e) {
                LOG.errorf("Pipeline failed for content: %s - %s", finalContentId, e.getMessage());
                try {
                    pipeline.markAsFailed(finalContentId, e.getMessage());
                } catch (Exception ex) {
                    LOG.errorf("Failed to mark content as failed: %s", ex.getMessage());
                }
            } finally {
                Arc.container().requestContext().terminate();
            }
        });
        
        LOG.infof("Pipeline triggered asynchronously for content: %s", contentId);
        return contentMapper.toResponse(content);
    }
    
    public ContentResponse getContent(String contentId) throws Exception {
        Content content = contentRepository.findByContentId(contentId);
        if (content == null) {
            return null;
        }
        return contentMapper.toResponse(content);
    }
    
    public InputStream getResult(String contentId, String signature) throws Exception {
        Content content = contentRepository.findByContentId(contentId);
        if (content == null) {
            throw new IllegalArgumentException("Content not found: " + contentId);
        }
        
        if (content.getStatus() != ContentStatus.SUCCEEDED) {
            throw new IllegalStateException("Content not ready: " + content.getStatus());
        }
        
        if (!signature.equals(content.getSignatureFetchResult())) {
            throw new SecurityException("Invalid signature");
        }
        
        if (content.getResultUrl() == null) {
            return null;
        }
        
        // Extract object key from URL
        String objectKey = content.getResultUrl().substring(content.getResultUrl().indexOf("/") + 1);
        return storageService.downloadResult(objectKey);
    }
    
    private ContentStatus determineInitialStatus(CreateContentRequest request) {
        // If templateId is provided and valid, go directly to QUEUED
        if (request.getTemplateId() != null && !request.getTemplateId().isEmpty()) {
            return ContentStatus.QUEUED;
        }
        
        // If output format is PNG, no template needed
        if (request.getOutputFormat().name().equals("PNG")) {
            return ContentStatus.QUEUED;
        }
        
        // Otherwise, wait for document upload
        return ContentStatus.WAITING_DOCUMENT;
    }
}
