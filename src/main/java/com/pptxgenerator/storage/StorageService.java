package com.pptxgenerator.storage;

import io.minio.*;
import io.minio.errors.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.InputStream;

@ApplicationScoped
public class StorageService {
    
    private static final Logger LOG = Logger.getLogger(StorageService.class);
    
    @ConfigProperty(name = "minio.url")
    String minioUrl;
    
    @ConfigProperty(name = "minio.access-key")
    String accessKey;
    
    @ConfigProperty(name = "minio.secret-key")
    String secretKey;
    
    @ConfigProperty(name = "minio.bucket.templates")
    String templatesBucket;
    
    @ConfigProperty(name = "minio.bucket.results")
    String resultsBucket;
    
    private MinioClient minioClient;
    
    private MinioClient getClient() {
        if (minioClient == null) {
            minioClient = MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(accessKey, secretKey)
                .build();
        }
        return minioClient;
    }
    
    public void uploadTemplate(String objectKey, InputStream inputStream, String contentType) throws Exception {
        uploadObject(templatesBucket, objectKey, inputStream, contentType);
    }
    
    public void uploadResult(String objectKey, InputStream inputStream, String contentType) throws Exception {
        uploadObject(resultsBucket, objectKey, inputStream, contentType);
    }
    
    private void uploadObject(String bucket, String objectKey, InputStream inputStream, String contentType) throws Exception {
        try {
            // Check if bucket exists, create if not
            boolean found = getClient().bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!found) {
                getClient().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            
            getClient().putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .stream(inputStream, -1, 10485760)
                .contentType(contentType)
                .build());
            
            LOG.infof("Uploaded object %s to bucket %s", objectKey, bucket);
        } catch (Exception e) {
            LOG.errorf("Failed to upload object %s to bucket %s: %s", objectKey, bucket, e.getMessage());
            throw e;
        }
    }
    
    public InputStream downloadTemplate(String objectKey) throws Exception {
        return downloadObject(templatesBucket, objectKey);
    }
    
    public InputStream downloadResult(String objectKey) throws Exception {
        return downloadObject(resultsBucket, objectKey);
    }
    
    private InputStream downloadObject(String bucket, String objectKey) throws Exception {
        try {
            return getClient().getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build());
        } catch (Exception e) {
            LOG.errorf("Failed to download object %s from bucket %s: %s", objectKey, bucket, e.getMessage());
            throw e;
        }
    }
    
    public void deleteTemplate(String objectKey) throws Exception {
        deleteObject(templatesBucket, objectKey);
    }
    
    public void deleteResult(String objectKey) throws Exception {
        deleteObject(resultsBucket, objectKey);
    }
    
    private void deleteObject(String bucket, String objectKey) throws Exception {
        try {
            getClient().removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build());
            
            LOG.infof("Deleted object %s from bucket %s", objectKey, bucket);
        } catch (Exception e) {
            LOG.errorf("Failed to delete object %s from bucket %s: %s", objectKey, bucket, e.getMessage());
            throw e;
        }
    }
    
    public String getTemplateUrl(String objectKey) {
        return templatesBucket + "/" + objectKey;
    }
    
    public String getResultUrl(String objectKey) {
        return resultsBucket + "/" + objectKey;
    }
}
