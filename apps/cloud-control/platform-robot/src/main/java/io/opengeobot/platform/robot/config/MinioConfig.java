/*
 * Function: MinIO configuration — S3-compatible client bean for F-MEDIA-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the MinIO S3-compatible client. Reads endpoint,
 * credentials, and bucket name from {@code platform.minio.*} properties.
 * The MinIO client bean is shared by {@code MediaService} for upload,
 * download, and delete operations.
 */
@Configuration
public class MinioConfig {

    @Value("${platform.minio.endpoint}")
    private String endpoint;

    @Value("${platform.minio.access-key}")
    private String accessKey;

    @Value("${platform.minio.secret-key}")
    private String secretKey;

    @Value("${platform.minio.bucket}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    public String getBucket() {
        return bucket;
    }
}
