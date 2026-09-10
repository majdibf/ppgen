package com.pptxgenerator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptxgenerator.common.exception.ContentNotFoundException;
import com.pptxgenerator.common.exception.ContentTokenUsedException;
import com.pptxgenerator.common.exception.InvalidTokenException;
import com.pptxgenerator.dto.response.ContentResponse;
import com.pptxgenerator.entity.Content;
import com.pptxgenerator.mapper.ContentMapper;
import com.pptxgenerator.model.enums.Operation;
import com.pptxgenerator.pipeline.ContentCreationPipeline;
import com.pptxgenerator.pipeline.PPTXPipelineResult;
import com.pptxgenerator.repository.ContentRepository;
import com.pptxgenerator.service.s3.S3ContentOutputStorage;
import com.pptxgenerator.service.s3.S3ContentTemplateStorage;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.nullable;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the real-project-aligned upload flow
 * ({@link ContentService#sendDocumentForContent}): CWE-434 upload controls,
 * signature validation, document token single-use and hostile filename sanitization.
 */
class ContentServiceUploadValidationTest {

    private static final byte[] ZIP_MAGIC = {'P', 'K', 0x03, 0x04};
    private static final String CONTENT_ID = "cnt_test";
    private static final String SIGNATURE = "sig_sd_ok";

    @TempDir
    Path tempDir;

    private ContentRepository repository;
    private S3ContentTemplateStorage s3ContentTemplateStorage;
    private S3ContentOutputStorage s3ContentOutputStorage;
    private Content content;

    @BeforeEach
    void setUp() {
        repository = mock(ContentRepository.class);
        s3ContentTemplateStorage = mock(S3ContentTemplateStorage.class);
        s3ContentOutputStorage = mock(S3ContentOutputStorage.class);
        content = new Content();
        content.setId(CONTENT_ID);
        content.setSignatureSendDocument(SIGNATURE);
        content.setDocumentTokenUsed(false);
        content.setResultTokenUsed(false);
        content.setOperation(Operation.CREATION);
        when(repository.findByContentId(CONTENT_ID)).thenReturn(content);
        // Deterministic key naming mirrored from the adapter
    }

    private ContentService service() {
        ContentCreationPipeline pipeline = mock(ContentCreationPipeline.class);
        when(pipeline.executeAsync(anyString())).thenReturn(Uni.createFrom().item(new PPTXPipelineResult(new byte[0])));
        ContentMapper mapper = mock(ContentMapper.class);
        when(mapper.toResponse(any(Content.class))).thenReturn(new ContentResponse());
        return new ContentService(repository, mapper,
                s3ContentTemplateStorage, s3ContentOutputStorage, pipeline);
    }

    /** Real upload material: OOXML magic header + tail, persisted as the request File. */
    private File pptxFile(String name, byte[] body) throws Exception {
        Path file = tempDir.resolve(name);
        Files.write(file, body, StandardOpenOption.CREATE);
        return file.toFile();
    }

    private byte[] validTemplate() {
        byte[] bytes = new byte[ZIP_MAGIC.length + 2];
        System.arraycopy(ZIP_MAGIC, 0, bytes, 0, ZIP_MAGIC.length);
        return bytes;
    }

    @Test
    void validPptx_passesValidation_keyUsesSanitizedName() throws Exception {
        ContentResponse response = serviceWithContent()
            .sendDocumentForContent(CONTENT_ID, pptxFile("template", validTemplate()),
                "Template (BPCE).pptx", SIGNATURE);

        assertThat(response).isNotNull();
        verify(s3ContentTemplateStorage).upload(any(File.class), contains("Template__BPCE_.pptx"), contains(CONTENT_ID));
    }

    private ContentService serviceWithContent() {
        return service();
    }

    /** No upload may be performed by any storage when controls reject the file. */
    private void verifyNoInteractionsNoUpload() {
        Mockito.verifyNoInteractions(s3ContentTemplateStorage);
        Mockito.verifyNoInteractions(s3ContentOutputStorage);
    }

    @Test
    void wrongSignature_invalidToken_beforeAnyTransfer() throws Exception {
        File valid = pptxFile("t.pptx", validTemplate());
        assertThatThrownBy(() -> service().sendDocumentForContent(CONTENT_ID, valid, "t.pptx", "sig_sd_bad"))
            .isInstanceOf(InvalidTokenException.class);
        verifyNoInteractionsNoUpload();
    }

    @Test
    void reusedDocumentToken_forbidden() throws Exception {
        ContentService svc = service();
        svc.sendDocumentForContent(CONTENT_ID, pptxFile("a.pptx", validTemplate()), "a.pptx", SIGNATURE);
        File second = pptxFile("b.pptx", validTemplate());
        assertThatThrownBy(() -> svc.sendDocumentForContent(CONTENT_ID, second, "b.pptx", SIGNATURE))
            .isInstanceOf(ContentTokenUsedException.class);
    }

    @Test
    void unknownContentId_notFound() throws Exception {
        assertThatThrownBy(() -> service().sendDocumentForContent("cnt_unknown",
                pptxFile("t.pptx", validTemplate()), "t.pptx", SIGNATURE))
            .isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void disallowedExtension_rejected() throws Exception {
        File file = pptxFile("virus.exe", validTemplate());
        assertThatThrownBy(() -> service().sendDocumentForContent(CONTENT_ID, file, "virus.exe", SIGNATURE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(".pptx");
        verifyNoInteractionsNoUpload();
    }

    @Test
    void emptyFile_rejected() throws Exception {
        assertThatThrownBy(() -> service().sendDocumentForContent(CONTENT_ID,
                pptxFile("t.pptx", new byte[0]), "t.pptx", SIGNATURE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
        verifyNoInteractionsNoUpload();
    }

    @Test
    void oversizedFile_rejected() throws Exception {
        File big = tempDir.resolve("big.pptx").toFile();
        try (var raf = new java.io.RandomAccessFile(big, "rw")) {
            raf.setLength(11L * 1024 * 1024);
        }
        assertThatThrownBy(() -> service().sendDocumentForContent(CONTENT_ID, big, "big.pptx", SIGNATURE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("limit");
        verifyNoInteractionsNoUpload();
    }

    @Test
    void nonZipContent_rejectedByMagicBytes() throws Exception {
        assertThatThrownBy(() -> service().sendDocumentForContent(CONTENT_ID,
                pptxFile("fake.pptx", "<html>zip bomb</html>".getBytes()), "fake.pptx", SIGNATURE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("OOXML");
        verifyNoInteractionsNoUpload();
    }

    @Test
    void hostileRelativePath_sanitizedToBasename() throws Exception {
        ContentResponse response = service().sendDocumentForContent(CONTENT_ID,
            pptxFile("up", validTemplate()), "../../etc/passwd.pptx", SIGNATURE);

        assertThat(response).isNotNull();
        verify(s3ContentTemplateStorage).upload(any(File.class), contains("passwd.pptx"), contains(CONTENT_ID));
    }

    @Test
    void dotFileName_rejected() throws Exception {
        assertThatThrownBy(() -> service().sendDocumentForContent(CONTENT_ID,
                pptxFile("t.pptx", validTemplate()), "..pptx", SIGNATURE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("file name");
    }
}