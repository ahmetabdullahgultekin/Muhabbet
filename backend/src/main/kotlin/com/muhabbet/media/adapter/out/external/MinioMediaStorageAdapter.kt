package com.muhabbet.media.adapter.out.external

import com.muhabbet.media.domain.port.out.MediaStoragePort
import com.muhabbet.shared.config.MediaProperties
import io.minio.BucketExistsArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.http.Method
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.util.concurrent.TimeUnit

@Component
class MinioMediaStorageAdapter(
    private val mediaProperties: MediaProperties
) : MediaStoragePort {

    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var client: MinioClient
    private lateinit var publicClient: MinioClient

    @PostConstruct
    fun init() {
        client = MinioClient.builder()
            .endpoint(mediaProperties.minio.endpoint)
            .credentials(mediaProperties.minio.accessKey, mediaProperties.minio.secretKey)
            .build()

        // Separate client for pre-signed URL generation using public endpoint
        val pubEndpoint = mediaProperties.minio.publicEndpoint
        publicClient = if (!pubEndpoint.isNullOrBlank()) {
            log.info("MinIO public endpoint configured: {}", pubEndpoint)
            MinioClient.builder()
                .endpoint(pubEndpoint)
                .credentials(mediaProperties.minio.accessKey, mediaProperties.minio.secretKey)
                .build()
        } else {
            client
        }

        val bucket = mediaProperties.minio.bucket
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
            log.info("Created MinIO bucket: {}", bucket)
        }
    }

    override fun putObject(key: String, inputStream: InputStream, contentType: String, sizeBytes: Long) {
        client.putObject(
            PutObjectArgs.builder()
                .bucket(mediaProperties.minio.bucket)
                .`object`(key)
                .stream(inputStream, sizeBytes, -1)
                .contentType(contentType)
                .build()
        )
    }

    override fun getPresignedUrl(key: String, expirySeconds: Int): String {
        return publicClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(mediaProperties.minio.bucket)
                .`object`(key)
                .expiry(expirySeconds, TimeUnit.SECONDS)
                .build()
        )
    }

    override fun deleteObject(key: String) {
        client.removeObject(
            RemoveObjectArgs.builder()
                .bucket(mediaProperties.minio.bucket)
                .`object`(key)
                .build()
        )
    }
}
