package com.muhabbet.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("muhabbet.media")
data class MediaProperties(
    val minio: MinioProperties = MinioProperties(),
    val retentionDays: Int = 30,
    val maxImageSize: Long = 10_485_760,
    val maxVideoSize: Long = 104_857_600,
    val thumbnailWidth: Int = 320,
    val thumbnailHeight: Int = 320
) {
    data class MinioProperties(
        val endpoint: String = "http://localhost:9000",
        val publicEndpoint: String? = null,
        val accessKey: String = "minioadmin",
        val secretKey: String = "minioadmin",
        val bucket: String = "muhabbet-media"
    )
}
