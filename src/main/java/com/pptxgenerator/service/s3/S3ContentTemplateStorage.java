package com.pptxgenerator.service.s3;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class S3ContentTemplateStorage extends S3ContentStorage {

    public static final String EXPECTED_FOLDER = S3_TEMPLATES_FOLDER;

    public S3ContentTemplateStorage(MinioClientProvider amazonS3Client) {
        super(amazonS3Client);
    }

    @Override
    String buildKey(String templateId, String fileName) {
        return S3_TEMPLATES_FOLDER
                .concat(SEPARATOR)
                .concat(templateId)
                .concat(SEPARATOR)
                .concat(fileName);
    }
}
