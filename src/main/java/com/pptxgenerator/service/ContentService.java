package com.pptxgenerator.service;

import com.pptxgenerator.common.exception.ContentNotFoundException;
import com.pptxgenerator.common.exception.ContentResultNotAvailableException;
import com.pptxgenerator.common.exception.ContentTokenUsedException;
import com.pptxgenerator.common.exception.DocumentUploadException;
import com.pptxgenerator.common.exception.InvalidTokenException;
import com.pptxgenerator.dto.request.CreateContentRequest;
import com.pptxgenerator.dto.response.ContentResponse;
import com.pptxgenerator.dto.response.ContentResultDto;
import com.pptxgenerator.entity.Content;
import com.pptxgenerator.entity.Template;
import com.pptxgenerator.mapper.ContentMapper;
import com.pptxgenerator.model.enums.ContentStatus;
import com.pptxgenerator.model.enums.FileType;
import com.pptxgenerator.model.enums.Operation;
import com.pptxgenerator.pipeline.ContentCreationPipeline;
import com.pptxgenerator.pipeline.PPTXPipelineResult;
import com.pptxgenerator.repository.ContentRepository;
import com.pptxgenerator.repository.TemplateRepository;
import com.pptxgenerator.service.s3.S3ContentOutputStorage;
import com.pptxgenerator.service.s3.S3ContentTemplateStorage;
import io.smallrye.mutiny.unchecked.Unchecked;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Aligned with the real project {@code ContentCreationService}: typed exceptions
 * (ContentNotFoundException, InvalidTokenException, ContentTokenUsedException,
 * DocumentUploadException), single-use token controls, per-helper short
 * transactions, and the Mutiny pipeline subscription inside
 * {@code sendDocumentForContent}.
 */
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
    private static final String REQUIRED_EXTENSION = ".pptx";
    private static final String TEMPLATE_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final byte[] OOXML_MAGIC = {'P', 'K', 0x03, 0x04};
    private static final String SEPARATOR = "/";
    private static final String S3_TEMPLATES_FOLDER = "templates";
    private static final String S3_CONTENT_OUTPUT_FOLDER = "contents/output";

    private final ContentRepository contentRepository;
    private final TemplateRepository templateRepository;
    private final ContentMapper contentMapper;
    private final S3ContentTemplateStorage s3ContentTemplateStorage;
    private final S3ContentOutputStorage s3ContentOutputStorage;
    private final ContentCreationPipeline pipeline;

    public ContentService(ContentRepository contentRepository,
                          TemplateRepository templateRepository,
                          ContentMapper contentMapper,
                          S3ContentTemplateStorage s3ContentTemplateStorage,
                          S3ContentOutputStorage s3ContentOutputStorage,
                          ContentCreationPipeline pipeline) {
        this.contentRepository = contentRepository;
        this.templateRepository = templateRepository;
        this.contentMapper = contentMapper;
        this.s3ContentTemplateStorage = s3ContentTemplateStorage;
        this.s3ContentOutputStorage = s3ContentOutputStorage;
        this.pipeline = pipeline;
    }

    @Transactional
    public ContentResponse createContent(CreateContentRequest request) throws Exception {
        log.info("Creating content with operation: {}", request.getOperation());

        String contentId = "cnt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        ContentStatus initialStatus = determineInitialStatus(request);

        String sendDocumentSignature = null;
        if (initialStatus == ContentStatus.WAITING_DOCUMENT) {
            sendDocumentSignature = "sig_sd_" + UUID.randomUUID().toString().replace("-", "");
        }

        Content content = contentMapper.toEntity(request);
        content.setId(contentId);
        content.setStatus(initialStatus);
        content.setSignatureSendDocument(sendDocumentSignature);
        content.setSubmittedAt(Instant.now());

        contentRepository.persist(content);
        log.info("Content created: {} with status: {}", contentId, initialStatus);

        return contentMapper.toResponse(content);
    }

    /**
     * Uploads the template document for a content and triggers the async pipeline.
     *
     * <p>Signature aligned with the real project
     * {@code ContentCreationService#sendDocumentForContent(externalId, document, fileName, signature)}:
     * NOT transactional at this level — each helper opens its own short transaction
     * (getContentByExternalId, checkAndMarkDocumentTokenUsed, updateContentFileName,
     * updateContentToRunning), so no transaction stays open when the Mutiny
     * subscription starts and when the controller returns. The CWE-434 upload
     * controls run BEFORE anything reaches the storage or the pipeline.
     */
    public ContentResponse sendDocumentForContent(String contentId, File document,
                                                  String fileName, String signature) throws DocumentUploadException {
        log.info("Started sendDocumentForContent: {}", fileName);

        Content content = getContentByExternalId(contentId);
        validateToken(content.getSignatureSendDocument(), signature);
        checkAndMarkDocumentTokenUsed(contentId);

        // CWE-434 controls: size, extension whitelist, magic bytes, sanitized filename
        String sanitizedFileName = validateUpload(document, fileName);

        // The uploaded file has its own identity: a Template entity with a templateId.
        // Storage key = templates/<templateId>/<fileName> (NOT the content id) — a
        // template is addressed by itself, exactly like the spec buildKey(templateId,
        // fileName), and one day shareable across contents.
        String templateId = nextTemplateId();
        s3ContentTemplateStorage.upload(document, sanitizedFileName, templateId);
        registerContentTemplate(contentId, templateId, sanitizedFileName, document);

        updateContentFileName(contentId, templateId, sanitizedFileName);

        // Launch async pipeline — aligned with the real project: the pipeline returns
        // the rendered bytes; the post-processing (S3 upload, fileName update, status
        // transitions) happens in the Mutiny chain, mirroring
        // uploadPptxToS3 / updateContentFileName / handlePipelineError.
        String outputFileName = contentId + ".pptx";

        pipeline.executeAsync(contentId)
                .onItem().invoke(Unchecked.consumer(
                        (PPTXPipelineResult result) -> uploadPptxToS3(result.pptxBytes(), outputFileName, contentId)))
                .onItem().invoke(Unchecked.consumer(
                        (PPTXPipelineResult result) -> updateContentToSuccess(contentId, outputFileName)))
                .onFailure().invoke(Unchecked.consumer(
                        (Throwable e) -> handlePipelineError(e, contentId)))
                .subscribe().with(
                        ok -> log.info("Content {} pipeline completed", contentId),
                        failure -> log.error("Content {} pipeline failed", contentId, failure));

        // Spec 6.3.2: POST /document answers { status: QUEUED } — snapshot BEFORE the
        // async RUNNING transition (real project returns an empty body so it doesn't
        // face the ordering issue; we return the full response contract).
        ContentResponse response = toResponse(contentId);

        // Real project calls updateContentToRunning right after subscribing.
        updateContentToRunning(contentId);

        log.info("Content {} document uploaded, pipeline triggered", contentId);
        return response;
    }

    /**
     * Aligned with the real project {@code uploadPptxToS3} — same call shape
     * ({@code upload(inputStream, length, fileName, contentId)}), output folder.
     */
    private void uploadPptxToS3(byte[] pptxBytes, String fileName, String contentExternalId) {
        try {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(pptxBytes)) {
                s3ContentOutputStorage.upload(inputStream, pptxBytes.length, fileName, contentExternalId);
                log.info("PPTX uploaded to storage: {} ({} bytes)", fileName, pptxBytes.length);
            }
        } catch (DocumentUploadException e) {
            throw new IllegalStateException("Failed to upload PPTX to storage: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload PPTX to storage", e);
        }
    }

    /**
     * Marks the content FAILED on any pipeline error, aligned with the real-project
     * {@code handlePipelineError}: never lets an error escape the Mutiny chain.
     */
    private void handlePipelineError(Throwable e, String contentId) {
        log.error("Pipeline error for content {}: {}", contentId, e.getMessage(), e);
        try {
            pipeline.markAsFailed(contentId, e.getMessage() != null ? e.getMessage() : e.toString());
        } catch (Exception ex) {
            log.error("Failed to mark content {} as FAILED", contentId, ex);
        }
    }
    @Transactional
    public Content getContentByExternalId(String externalId) {
        Content content = contentRepository.findByContentId(externalId);
        if (content == null) {
            throw new ContentNotFoundException(externalId);
        }
        return content;
    }

    /**
     * Registers the uploaded document as a Template row (spec 6.6 data model): the
     * pipeline can then recover the file by templateId + template.fileUrl, and the
     * future template_analysis caching lands here naturally.
     */
    @Transactional
    void registerContentTemplate(String contentExternalId, String templateId, String fileName, File document) {
        String fileUrl = S3_TEMPLATES_FOLDER + SEPARATOR + templateId + SEPARATOR + fileName;

        Template template = Template.builder()
                .id(templateId)
                .name(fileName)
                .fileType(FileType.PPTX)
                .fileUrl(fileUrl)
                .fileSizeBytes(document.length())
                .fileName(fileName)
                .build();
        templateRepository.persist(template);

        Content content = getContentByExternalId(contentExternalId);
        content.setTemplateId(templateId);
        log.info("Template {} registered for content {}", templateId, contentExternalId);
    }

    /** Deterministic, non-crediting id generator for template rows. */
    private String nextTemplateId() {
        return "tpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    /**
     * Aligned with the real project {@code validateToken}: mismatch → InvalidTokenException.
     */
    void validateToken(final String expectedToken, final String providedToken) {
        if (expectedToken == null || !expectedToken.equals(providedToken)) {
            throw new InvalidTokenException("Invalid token");
        }
    }

    /**
     * Single-use control: the document-upload signature can be consumed once.
     */
    @Transactional
    void checkAndMarkDocumentTokenUsed(String contentExternalId) {
        Content content = getContentByExternalId(contentExternalId);
        if (Boolean.TRUE.equals(content.getDocumentTokenUsed())) {
            throw new ContentTokenUsedException("Document token already used. ContentId: %s"
                    .formatted(content.getId()));
        }
        content.setDocumentTokenUsed(true);
    }

    /**
     * Real-project helper mapped to the closest field we persist: the upload file
    /**
     * Aligned with the real project {@code updateContentFileName}: the file name
     * lands in the persisted {@code file_name} column (aligned with their entity),
     * plus our legacy {@code document_url} kept populated for the API contract.
     */
    @Transactional
    void updateContentFileName(String contentExternalId, String templateId, String fileName) {
        Content content = getContentByExternalId(contentExternalId);
        content.setFileName(fileName);
        content.setDocumentUrl(S3_TEMPLATES_FOLDER + SEPARATOR
                + templateId + SEPARATOR + fileName);
        log.info("Content {} fileName updated to: {}", contentExternalId, fileName);
    }

    /**
     * Aligned with the real project {@code updateContentToRunning}: RUNNING is set
     * right after subscribing (the pipeline no longer touches statuses).
     */
    @Transactional
    void updateContentToRunning(String contentExternalId) {
        Instant now = Instant.now();
        Content content = getContentByExternalId(contentExternalId);
        content.setStartedAt(now);
        content.setQueuedAt(now);
        content.setStatus(ContentStatus.RUNNING);
        log.info("Content {} status updated to RUNNING", contentExternalId);
    }

    /**
     * Aligned with the real project: the chain updates the content file name to the
     * OUTPUT name ({@code <externalId>.pptx}), not the uploaded template name, then
     * marks SUCCEEDED. The download endpoint resolves by (contentId, fileName).
     */
    @Transactional
    void updateContentToSuccess(String contentExternalId, String outputFileName) {
        Content content = getContentByExternalId(contentExternalId);
        content.setResultUrl(S3_CONTENT_OUTPUT_FOLDER + SEPARATOR
                + contentExternalId + SEPARATOR + outputFileName);
        content.setFileName(outputFileName);
        content.setStatus(ContentStatus.SUCCEEDED);
        content.setEndedAt(Instant.now());
        content.setSignatureFetchResult("sig_fr_" + UUID.randomUUID().toString().replace("-", ""));
        log.info("Content {} status updated to SUCCEEDED", contentExternalId);
    }

    // ========================================================================
    // READ / VALIDATION HELPERS
    // ========================================================================

    /**
     * Upload controls (CWE-434): size, extension whitelist and file signature
     * (magic bytes). The client-supplied filename is sanitized before being used
     * as the storage object key.
     */
    private String validateUpload(File document, String fileName) {
        if (document == null || !document.exists()) {
            throw new IllegalArgumentException("Missing document file");
        }
        try {
            long actualSize = Files.size(document.toPath());
            if (actualSize == 0) {
                throw new IllegalArgumentException("Uploaded file is empty");
            }
            if (actualSize > MAX_UPLOAD_SIZE_BYTES) {
                throw new IllegalArgumentException(
                    "File exceeds the " + MAX_UPLOAD_SIZE_BYTES / (1024 * 1024) + " MB limit");
            }
            if (!isZipSignature(document.toPath())) {
                throw new IllegalArgumentException("Invalid file content: not an OOXML document");
            }
        } catch (IOException e) {
            throw new DocumentUploadException("Failed to validate uploaded file", e);
        }

        String sanitized = sanitizeFileName(fileName);
        if (!sanitized.toLowerCase().endsWith(REQUIRED_EXTENSION)) {
            throw new IllegalArgumentException("Only " + REQUIRED_EXTENSION + " templates are accepted in V1");
        }
        return sanitized;
    }

    /**
     * Keeps only a safe basename and allows a restricted character set, so a
     * hostile filename can never smuggle path separators or control characters
     * into the storage object key.
     */
    private String sanitizeFileName(String original) {
        if (original == null || original.isBlank() || original.chars().anyMatch(c -> c < 32)) {
            throw new IllegalArgumentException("Invalid template file name");
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

    private boolean isZipSignature(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] header = new byte[4];
            int read = is.read(header);
            return read == 4 && MessageDigest.isEqual(header, OOXML_MAGIC);
        }
    }

    private ContentResponse toResponse(String contentId) {
        return contentMapper.toResponse(getContentByExternalId(contentId));
    }

    // ========================================================================
    // READ ENDPOINTS
    // ========================================================================

    public ContentResponse getContent(String contentId) throws Exception {
        Content content = contentRepository.findByContentId(contentId);
        if (content == null) {
            return null;
        }
        return contentMapper.toResponse(content);
    }

    /**
     * Aligned with the real project {@code getContentResult}: signature validation,
     * status guard and single-use consumption of the result token, then the file
     * bytes download.
     */
    @Transactional
    public ContentResultDto getContentResult(String contentExternalId, String signature) throws Exception {
        Content content = getContentByExternalId(contentExternalId);

        validateToken(content.getSignatureFetchResult(), signature);
        checkContentStatus(content);
        checkResultTokenUsed(content);

        byte[] outputFile = downloadResultBytes(content);
        content.setResultTokenUsed(true);

        return ContentResultDto.builder()
                .id(contentExternalId)
                .content(outputFile)
                .fileName(extractFileName(content.getResultUrl()))
                .build();
    }

    void checkContentStatus(Content content) {
        if (content.getStatus() != ContentStatus.SUCCEEDED) {
            throw new ContentResultNotAvailableException(content.getId());
        }
    }

    void checkResultTokenUsed(Content content) {
        if (Boolean.TRUE.equals(content.getResultTokenUsed())) {
            throw new ContentTokenUsedException("Result token already used");
        }
    }

    private byte[] downloadResultBytes(Content content) {
        try (InputStream is = s3ContentOutputStorage.downloadAsInputStream(
                content.getId(), content.getFileName() != null ? content.getFileName() : content.getId() + ".pptx")) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new DocumentUploadException("Failed to download content result", e);
        }
    }

    private String extractObjectKey(String resultUrl) {
        return resultUrl.substring(resultUrl.indexOf("/") + 1);
    }

    private String extractFileName(String resultUrl) {
        String key = extractObjectKey(resultUrl);
        int lastSlash = key.lastIndexOf('/');
        return lastSlash >= 0 ? key.substring(lastSlash + 1) : key;
    }

    private ContentStatus determineInitialStatus(CreateContentRequest request) {
        if (request.getTemplateId() != null && !request.getTemplateId().isEmpty()) {
            // Spec 6.3.1 CAS 2 reserves templateId for the template library (future
            // /templates endpoints). Until they exist, rejecting is safer than leaving
            // the content stuck QUEUED with no pipeline trigger.
            throw new IllegalArgumentException(
                "templateId is not supported yet (template library out of scope); "
                    + "upload a template document instead");
        }

        // If output format is PNG, no template needed
        if (request.getOutputFormat().name().equals("PNG")) {
            return ContentStatus.QUEUED;
        }

        // Otherwise, wait for document upload
        return ContentStatus.WAITING_DOCUMENT;
    }
}
