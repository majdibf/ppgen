package com.pptxgenerator.service.s3;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class S3ContentOutputStorage extends S3ContentStorage {

    public S3ContentOutputStorage(MinioClientProvider amazonS3Client) {
        super(amazonS3Client);
    }

    @Override
    String buildKey(String contentId, String fileName) {
        return S3_CONTENT_OUTPUT_FOLDER
                .concat(SEPARATOR)
                .concat(contentId)
                .concat(SEPARATOR)
                .concat(fileName);
    }
}
