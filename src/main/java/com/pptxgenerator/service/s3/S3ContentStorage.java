package com.pptxgenerator.service.s3;

import com.pptxgenerator.common.exception.DocumentDownloadException;
import com.pptxgenerator.common.exception.DocumentUploadException;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.GetObjectArgs;
import io.minio.PutObjectArgs;
import io.minio.MinioClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * MinIO-backed base storage, port of the real project {@code S3ContentStorage}:
 * one bucket with folder prefixes, object keys built by the subclasses
 * ({@code buildKey}), upload/download aligned with the same API surface.
 */
@RequiredArgsConstructor
@Slf4j
public abstract class S3ContentStorage {

    protected static final String S3_TEMPLATES_FOLDER = "templates";
    protected static final String S3_CONTENT_INPUT_FOLDER = "contents/input";
    protected static final String S3_CONTENT_OUTPUT_FOLDER = "contents/output";

    protected static final String SEPARATOR = "/";

    protected static final String PPTX_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    private final MinioClientProvider clientProvider;

    public void upload(File file, String fileName, String contentId) throws DocumentUploadException {
        log.info("Start uploading document {} for contentId {}", fileName, contentId);
        try (InputStream in = new FileInputStream(file)) {
            putObject(buildPutObject(contentId, fileName), in, file.length(), PPTX_CONTENT_TYPE);
            log.info("End uploading document {} for contentId {}", fileName, contentId);
        } catch (Exception e) {
            log.error("Error uploading document {} for contentId {}", fileName, contentId, e);
            throw new DocumentUploadException(contentId, e);
        }
    }

    /**
     * Upload from InputStream with explicit content length.
     *
     * @param inputStream  stream containing the file data
     * @param contentLength size of the data in bytes
     * @param fileName     name of the file
     * @param contentId    content identifier
     */
    public void upload(InputStream inputStream, long contentLength, String fileName, String contentId)
            throws DocumentUploadException {
        log.info("Start uploading document {} for contentId {} ({} bytes)", fileName, contentId, contentLength);
        try {
            putObject(buildPutObject(contentId, fileName), inputStream, contentLength, PPTX_CONTENT_TYPE);
            log.info("End uploading document {} for contentId {}", fileName, contentId);
        } catch (Exception e) {
            log.error("Error uploading document {} for contentId {}", fileName, contentId, e);
            throw new DocumentUploadException(contentId, e);
        }
    }

    /**
     * Streaming download for a controller response. The {@link StreamingOutput}
     * self-closes the MinIO stream (improvement over the real-project version,
     * whose streamed lambda leaked the S3 stream on failure).
     */
    public StreamingOutput download(String contentId, String fileName) throws DocumentDownloadException {
        try {
            log.info("Start downloading document {} for contentId {}", fileName, contentId);
            InputStream s3Stream = getObject(buildGetObjectRequest(contentId, fileName));
            log.info("End downloading document {} for contentId {}", fileName, contentId);
            return output -> {
                try (s3Stream) {
                    s3Stream.transferTo(output);
                }
            };
        } catch (Exception e) {
            log.error("Error downloading document {} for contentId {}", fileName, contentId, e);
            throw new DocumentDownloadException(contentId, e);
        }
    }

    public InputStream downloadAsInputStream(String contentId, String fileName) throws DocumentDownloadException {
        try {
            log.info("Start downloading document {} as InputStream for contentId {}", fileName, contentId);
            InputStream s3Stream = getObject(buildGetObjectRequest(contentId, fileName));
            log.info("End downloading document {} as InputStream for contentId {}", fileName, contentId);
            return s3Stream;
        } catch (Exception e) {
            log.error("Error downloading document {} as InputStream for contentId {}", fileName, contentId, e);
            throw new DocumentDownloadException(contentId, e);
        }
    }

    private InputStream getObject(String objectKey) throws Exception {
        return clientProvider.getInstance().getObject(GetObjectArgs.builder()
                .bucket(clientProvider.getBucketName())
                .object(objectKey)
                .build());
    }

    private void putObject(String objectKey, InputStream inputStream, long contentLength,
                           String contentType) throws Exception {
        MinioClient s3ClientInstance = clientProvider.getInstance();
        String bucket = clientProvider.getBucketName();

        boolean found = s3ClientInstance.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!found) {
            s3ClientInstance.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }

        s3ClientInstance.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .stream(inputStream, contentLength, -1)
                .contentType(contentType)
                .build());
    }

    private String buildGetObjectRequest(String contentId, String fileName) {
        return buildKey(contentId, fileName);
    }

    private String buildPutObject(String contentId, String fileName) {
        return buildKey(contentId, fileName);
    }

    abstract String buildKey(String documentId, String fileName);
}