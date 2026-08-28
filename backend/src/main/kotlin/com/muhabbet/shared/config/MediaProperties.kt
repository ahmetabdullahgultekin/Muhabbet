package com.muhabbet.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("muhabbet.media")
data class MediaProperties(
    val minio: MinioProperties = MinioProperties(),
    val retentionDays: Int = 30,
    val maxImageSize: Long = 10_485_760,
    val maxVideoSize: Long = 104_857_600,
    val thumbnailWidth: Int = 320,
    val thumbnailHeight: Int = 320,
    /**
     * Origins other than our own MinIO that a message or status may point its media at (#679).
     *
     * A sender may not choose the address a recipient's phone connects to; the send path refuses
     * anything outside our own media host. GIFs and stickers are the one thing the app itself sends
     * from somewhere else — `GifStickerPicker` picks a URL straight off GIPHY's CDN and never
     * re-hosts it — so without these entries the fix would silently stop GIFs and stickers from
     * being sent at all.
     *
     * The three harms in #679 all need the *sender* to own the server the recipient talks to:
     * their IP in the sender's log, the moment of reading in the sender's log, media that is not
     * where we think it is. A fixed third party does not give the sender any of that — anyone can
     * upload a GIF to GIPHY, and nobody who does gets to read GIPHY's access logs. That is what
     * makes this list safe and what makes it a *list*, closed and configured, rather than a
     * relaxation of the rule.
     *
     * GIPHY serves the same object from several numbered hosts, so each is named. A new one would
     * make that GIF unsendable — a visible, bounded failure — rather than opening a hole.
     */
    val attachmentOrigins: List<String> = listOf(
        "https://media.giphy.com",
        "https://media0.giphy.com",
        "https://media1.giphy.com",
        "https://media2.giphy.com",
        "https://media3.giphy.com",
        "https://media4.giphy.com",
        "https://i.giphy.com",
        "https://stickers.giphy.com"
    )
) {
    data class MinioProperties(
        val endpoint: String = "http://localhost:9000",
        val publicEndpoint: String? = null,
        val accessKey: String = "minioadmin",
        val secretKey: String = "minioadmin",
        val bucket: String = "muhabbet-media"
    )
}
