package com.muhabbet.shared.validation

/**
 * Validation rules shared between backend and mobile.
 * Backend uses these in service layer; mobile uses for local validation before sending.
 */
object ValidationRules {

    // Phone number: Turkish E.164 format
    private val TURKISH_PHONE_REGEX = Regex("^\\+90[5][0-9]{9}$")

    fun isValidTurkishPhone(phone: String): Boolean =
        TURKISH_PHONE_REGEX.matches(phone)

    // OTP
    const val OTP_LENGTH = 6
    private val OTP_REGEX = Regex("^[0-9]{$OTP_LENGTH}$")

    fun isValidOtp(otp: String): Boolean =
        OTP_REGEX.matches(otp)

    // Display name
    const val DISPLAY_NAME_MIN = 1
    const val DISPLAY_NAME_MAX = 64

    fun isValidDisplayName(name: String): Boolean =
        name.length in DISPLAY_NAME_MIN..DISPLAY_NAME_MAX && name.isNotBlank()

    // About / status text
    const val ABOUT_MAX = 256

    fun isValidAbout(about: String): Boolean =
        about.length <= ABOUT_MAX

    // Message content
    const val MESSAGE_MAX_LENGTH = 10_000

    fun isValidMessageContent(content: String): Boolean =
        content.isNotBlank() && content.length <= MESSAGE_MAX_LENGTH

    // Group name
    const val GROUP_NAME_MIN = 1
    const val GROUP_NAME_MAX = 128

    fun isValidGroupName(name: String): Boolean =
        name.length in GROUP_NAME_MIN..GROUP_NAME_MAX && name.isNotBlank()

    // Group size
    const val MAX_GROUP_MEMBERS = 256

    // Media
    const val MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024      // 10 MB
    const val MAX_VIDEO_SIZE_BYTES = 100L * 1024 * 1024     // 100 MB
    const val MAX_DOCUMENT_SIZE_BYTES = 100L * 1024 * 1024  // 100 MB
    const val MAX_VOICE_SIZE_BYTES = 16L * 1024 * 1024      // 16 MB

    val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    val ALLOWED_VIDEO_TYPES = setOf("video/mp4", "video/quicktime")
    val ALLOWED_VOICE_TYPES = setOf("audio/ogg", "audio/opus", "audio/mp4")
}
