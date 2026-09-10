package com.pptxgenerator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptxgenerator.dto.request.CreateContentRequest;
import com.pptxgenerator.dto.response.ContentResponse;
import com.pptxgenerator.entity.Content;
import com.pptxgenerator.mapper.ContentMapper;
import com.pptxgenerator.model.enums.ContentStatus;
import com.pptxgenerator.pipeline.ContentCreationPipeline;
import com.pptxgenerator.repository.ContentRepository;
import com.pptxgenerator.storage.StoragePort;
import io.quarkus.arc.Arc;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class ContentService {

    /**
     * Upload controls (CWE-434): the V1 API accepts only PowerPoint templates,
     * whose content is later parsed by docx4j. An unvalidated OOXML upload is an
     * XXE/zip-bomb/DoS surface, so extension, size and file signature are checked
     * before the byte stream reaches the storage or the pipeline.
     */
    private static final long MAX_UPLOAD_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Pattern SAFE_FILENAME = Pattern.compile("[A-Za-z0-9._-]+");
    private static final String REQUIRED_EXTENSION = ".pptx";
    private static final byte[] OOXML_MAGIC = {'P', 'K', 0x03, 0x04};
    
    private final ContentRepository contentRepository;
    private final ContentMapper contentMapper;
    private final StoragePort storageService;
    private final ObjectMapper objectMapper;
    private final ContentCreationPipeline pipeline;
    private final ExecutorService executorService;
    
    public ContentService(ContentRepository contentRepository,
                         ContentMapper contentMapper,
                         StoragePort storageService,
                         ContentCreationPipeline pipeline) {
        this(contentRepository, contentMapper, storageService, pipeline, Executors.newFixedThreadPool(5));
    }

    ContentService(ContentRepository contentRepository,
                   ContentMapper contentMapper,
                   StoragePort storageService,
                   ContentCreationPipeline pipeline,
                   ExecutorService executorService) {
        this.contentRepository = contentRepository;
        this.contentMapper = contentMapper;
        this.storageService = storageService;
        this.objectMapper = new ObjectMapper();
        this.pipeline = pipeline;
        this.executorService = executorService;
    }
    
    @Transactional
    public ContentResponse createContent(CreateContentRequest request) throws Exception {
        log.info("Creating content with operation: %s", request.getOperation());
        
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
        
        log.info("Content created: %s with status: %s", contentId, initialStatus);
        
        return contentMapper.toResponse(content);
    }
    
    @Transactional
    public ContentResponse uploadDocument(String contentId, String signature, FileUpload file) throws Exception {
        log.info("Uploading document for content: %s", contentId);
        
        // Get content from database
        Content content = contentRepository.findByContentId(contentId);
        if (content == null) {
            throw new IllegalArgumentException("Content not found: " + contentId);
        }
        
        // Validate signature (constant-time to avoid timing attacks)
        if (signature == null || content.getSignatureSendDocument() == null
                || !MessageDigest.isEqual(
                    signature.getBytes(StandardCharsets.UTF_8),
                    content.getSignatureSendDocument().getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid signature");
        }

        // Upload controls before anything touches storage or the pipeline
        String fileName = validateUpload(file);

        // Upload to storage
        String objectKey = "documents/" + contentId + "/" + fileName;
        try (InputStream inputStream = Files.newInputStream(file.filePath())) {
            storageService.uploadTemplate(objectKey, inputStream, file.contentType());
        }
        
        // Update content
        content.setDocumentUrl(storageService.getTemplateUrl(objectKey));
        content.setStatus(ContentStatus.QUEUED);
        content.setQueuedAt(Instant.now());
        
        content = contentRepository.update(content);
        
        log.info("Document uploaded for content: %s, triggering async pipeline", contentId);
        
        // Trigger async pipeline processing in a separate thread with CDI context
        final String finalContentId = contentId;
        executorService.submit(() -> {
            // Activate CDI request context for this thread
            Arc.container().requestContext().activate();
            try {
                log.info("Starting pipeline execution for content: %s", finalContentId);
                pipeline.executePipeline(finalContentId);
                log.info("Pipeline completed successfully for content: %s", finalContentId);
            } catch (Exception e) {
                log.error("Pipeline failed for content: %s - %s", finalContentId, e.getMessage());
                try {
                    pipeline.markAsFailed(finalContentId, e.getMessage());
                } catch (Exception ex) {
                    log.error("Failed to mark content as failed: %s", ex.getMessage());
                }
            } finally {
                Arc.container().requestContext().terminate();
            }
        });
        
        log.info("Pipeline triggered asynchronously for content: %s", contentId);
        return contentMapper.toResponse(content);
    }

    /**
     * Upload controls (CWE-434): size, extension whitelist and file signature
     * (magic bytes). The client-supplied filename is sanitized before being used
     * as the storage object key.
     *
     * @return the sanitized filename
     */
    private String validateUpload(FileUpload file) throws Exception {
        if (file == null || file.filePath() == null) {
            throw new IllegalArgumentException("Missing document file");
        }

        long actualSize = Files.size(file.filePath());
        if (actualSize == 0) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        if (actualSize > MAX_UPLOAD_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds the " + MAX_UPLOAD_SIZE_BYTES / (1024 * 1024) + " MB limit");
        }

        String sanitized = sanitizeFileName(file.fileName());
        if (!sanitized.toLowerCase().endsWith(REQUIRED_EXTENSION)) {
            throw new IllegalArgumentException("Only " + REQUIRED_EXTENSION + " templates are accepted in V1");
        }

        if (!isZipSignature(file.filePath())) {
            throw new IllegalArgumentException("Invalid file content: not an OOXML document");
        }
        return sanitized;
    }

    /**
     * Keeps only a safe basename and allows a restricted character set, so a
     * hostile filename can never smuggle path separators or control characters
     * into the storage object key.
     */
    private String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            throw new IllegalArgumentException("Missing template file name");
        }
        String base = original;
        int lastSep = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (lastSep >= 0) {
            base = base.substring(lastSep + 1);
        }
        String filtered = base.chars()
            .filter(c -> c >= 32 && c < 127)
            .mapToObj(c -> String.valueOf((char) c))
            .collect(java.util.stream.Collectors.joining())
            .replaceAll("[^A-Za-z0-9._-]", "_");
        if (filtered.isBlank() || filtered.startsWith(".")) {
            throw new IllegalArgumentException("Invalid template file name");
        }
        return filtered;
    }

    private boolean isZipSignature(Path filePath) throws Exception {
        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] header = new byte[4];
            int read = is.read(header);
            return read == 4 && MessageDigest.isEqual(header, OOXML_MAGIC);
        }
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
