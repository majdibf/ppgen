package com.pptxgenerator.storage;

import java.io.InputStream;

/**
 * Port (hexagonal boundary) for binary storage of templates and generated results.
 *
 * <p>The pipeline and services depend on this interface, never on the MinIO implementation, so the
 * adapter can be swapped or mocked in tests.
 */
public interface StoragePort {

    void uploadTemplate(String objectKey, InputStream inputStream, String contentType) throws Exception;

    void uploadResult(String objectKey, InputStream inputStream, String contentType) throws Exception;

    InputStream downloadTemplate(String objectKey) throws Exception;

    InputStream downloadResult(String objectKey) throws Exception;

    void deleteTemplate(String objectKey) throws Exception;

    void deleteResult(String objectKey) throws Exception;

    String getTemplateUrl(String objectKey);

    String getResultUrl(String objectKey);
}
