package com.pptxgenerator.service;

import com.pptxgenerator.entity.Content;
import com.pptxgenerator.mapper.ContentMapper;
import com.pptxgenerator.pipeline.ContentCreationPipeline;
import com.pptxgenerator.repository.ContentRepository;
import com.pptxgenerator.storage.StoragePort;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the CWE-434 upload controls in {@link ContentService#uploadDocument}:
 * constant-time signature check, size limit, extension whitelist, OOXML magic bytes
 * and hostile filename sanitization.
 */
class ContentServiceUploadValidationTest {

    private static final byte[] ZIP_MAGIC = {'P', 'K', 0x03, 0x04};
    private static final String CONTENT_ID = "cnt_test";
    private static final String SIGNATURE = "sig_sd_ok";

    @TempDir
    Path tempDir;

    private ContentRepository repository;
    private StoragePort storage;

    @BeforeEach
    void setUp() {
        repository = mock(ContentRepository.class);
        storage = mock(StoragePort.class);
        Content content = new Content();
        content.setId(CONTENT_ID);
        content.setSignatureSendDocument(SIGNATURE);
        when(repository.findByContentId(CONTENT_ID)).thenReturn(content);
    }

    private ContentService serviceThatDiscardsJobs() {
        return new ContentService(repository, mock(ContentMapper.class), storage,
            mock(ContentCreationPipeline.class), INLINE_EXECUTOR);
    }

    /** Daemon pool shared by all tests: the happy-path job runs on a mocked pipeline, so it is a no-op. */
    private static final ExecutorService INLINE_EXECUTOR =
            Executors.newFixedThreadPool(1, task -> {
                Thread thread = new Thread(task);
                thread.setDaemon(true);
                return thread;
            });

    private FileUpload upload(String fileName, byte[] bytes) throws Exception {
        Path file = tempDir.resolve("up-" + System.nanoTime() + ".bin");
        Files.write(file, bytes);
        return uploadFrom(fileName, file);
    }

    private FileUpload sparseUpload(String fileName, long size) throws Exception {
        Path file = tempDir.resolve("up-" + System.nanoTime() + ".bin");
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.setLength(size);
        }
        return uploadFrom(fileName, file);
    }

    private FileUpload uploadFrom(String fileName, Path file) {
        FileUpload upload = mock(FileUpload.class);
        when(upload.fileName()).thenReturn(fileName);
        when(upload.filePath()).thenReturn(file);
        return upload;
    }

    @Test
    void validPptx_passesValidation_keyUsesSanitizedName() throws Exception {
        byte[] template = new byte[ZIP_MAGIC.length + 2];
        System.arraycopy(ZIP_MAGIC, 0, template, 0, ZIP_MAGIC.length);

        serviceThatDiscardsJobs().uploadDocument(CONTENT_ID, SIGNATURE, upload("Template (BPCE).pptx", template));

        verify(storage).uploadTemplate(contains("documents/" + CONTENT_ID + "/Template__BPCE_.pptx"),
            any(), nullable(String.class));
    }

    @Test
    void wrongSignature_rejectedBeforeAnyTransfer() throws Exception {
        FileUpload valid = upload("t.pptx", ZIP_MAGIC);
        assertThatThrownBy(() -> serviceThatDiscardsJobs().uploadDocument(CONTENT_ID, "sig_sd_bad", valid))
            .isInstanceOf(SecurityException.class);
        verify(storage, never()).uploadTemplate(anyString(), any(), nullable(String.class));
    }

    @Test
    void missingFile_rejected() throws Exception {
        FileUpload unused = upload("t.pptx", ZIP_MAGIC);
        assertThatThrownBy(() -> serviceThatDiscardsJobs().uploadDocument(CONTENT_ID, SIGNATURE, null))
            .isInstanceOf(IllegalArgumentException.class);
        verify(storage, never()).uploadTemplate(anyString(), any(), nullable(String.class));
    }

    @Test
    void disallowedExtension_rejected() throws Exception {
        assertThatThrownBy(() -> serviceThatDiscardsJobs().uploadDocument(CONTENT_ID, SIGNATURE,
                upload("virus.exe", ZIP_MAGIC)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(".pptx");
        verify(storage, never()).uploadTemplate(anyString(), any(), nullable(String.class));
    }

    @Test
    void emptyFile_rejected() throws Exception {
        assertThatThrownBy(() -> serviceThatDiscardsJobs().uploadDocument(CONTENT_ID, SIGNATURE,
                upload("t.pptx", new byte[0])))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
        verify(storage, never()).uploadTemplate(anyString(), any(), nullable(String.class));
    }

    @Test
    void oversizedFile_rejected() throws Exception {
        FileUpload upload = sparseUpload("big.pptx", 11L * 1024 * 1024);
        assertThatThrownBy(() -> serviceThatDiscardsJobs().uploadDocument(CONTENT_ID, SIGNATURE, upload))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("limit");
        verify(storage, never()).uploadTemplate(anyString(), any(), nullable(String.class));
    }

    @Test
    void nonZipContent_rejectedByMagicBytes() throws Exception {
        assertThatThrownBy(() -> serviceThatDiscardsJobs().uploadDocument(CONTENT_ID, SIGNATURE,
                upload("fake.pptx", "<html>zip bomb</html>".getBytes())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("OOXML");
        verify(storage, never()).uploadTemplate(anyString(), any(), nullable(String.class));
    }

    @Test
    void hostileRelativePath_sanitizedToBasename() throws Exception {
        byte[] template = new byte[ZIP_MAGIC.length + 2];
        System.arraycopy(ZIP_MAGIC, 0, template, 0, ZIP_MAGIC.length);

        serviceThatDiscardsJobs().uploadDocument(CONTENT_ID, SIGNATURE, upload("../../etc/passwd.pptx", template));

        verify(storage).uploadTemplate(contains("documents/" + CONTENT_ID + "/passwd.pptx"),
            any(), nullable(String.class));
    }

    @Test
    void dotFileName_rejected() throws Exception {
        assertThatThrownBy(() -> serviceThatDiscardsJobs().uploadDocument(CONTENT_ID, SIGNATURE,
                upload("..pptx", ZIP_MAGIC)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("file name");
    }

    @Test
    void unknownContentId_rejectedBeforeSignature() throws Exception {
        FileUpload valid = upload("t.pptx", ZIP_MAGIC);
        assertThatThrownBy(() -> serviceThatDiscardsJobs().uploadDocument("cnt_unknown", SIGNATURE, valid))
            .isInstanceOf(IllegalArgumentException.class);
    }
}