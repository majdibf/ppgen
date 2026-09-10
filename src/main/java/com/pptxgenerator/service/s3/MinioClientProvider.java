package com.pptxgenerator.service.s3;

import io.minio.MinioClient;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;

/**
 * MinIO client holder, the counterpart of the real project {@code AmazonS3Client}
 * wrapper: lazy client instances, single bucket name. MinIO clients are thread-safe
 * and reused across calls (improvement over the per-call instantiation).
 */
@Slf4j
@ApplicationScoped
public class MinioClientProvider {

    @ConfigProperty(name = "minio.url")
    String minioUrl;

    @ConfigProperty(name = "minio.access-key")
    String accessKey;

    @ConfigProperty(name = "minio.secret-key")
    String secretKey;

    @ConfigProperty(name = "minio.bucket")
    String bucketName;

    private MinioClient instance;

    public synchronized MinioClient getInstance() {
        if (instance == null) {
            instance = MinioClient.builder()
                    .endpoint(minioUrl)
                    .credentials(accessKey, secretKey)
                    .build();
        }
        return instance;
    }

    public String getBucketName() {
        return bucketName;
    }
}
