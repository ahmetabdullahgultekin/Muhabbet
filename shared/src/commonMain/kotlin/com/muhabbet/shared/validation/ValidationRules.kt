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

    // Two-step verification PIN
    //
    // The screen enforced "6 digits" on its own and the server enforced nothing, so any string at
    // all — empty included — could be hashed and stored as somebody's second factor by a caller
    // that was not the app (#544). Stated once here so both halves agree on what a PIN is.
    const val TWO_STEP_PIN_LENGTH = 6
    private val TWO_STEP_PIN_REGEX = Regex("^[0-9]{$TWO_STEP_PIN_LENGTH}$")

    fun isValidTwoStepPin(pin: String): Boolean =
        TWO_STEP_PIN_REGEX.matches(pin)

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

    // Community name
    //
    // Its own bound rather than the group one: `communities.name` is VARCHAR(256), so borrowing the
    // 128-char group limit would reject names the column accepts, and borrowing nothing would let a
    // 300-char name reach Postgres and fail as a 500 instead of a 400.
    const val COMMUNITY_NAME_MIN = 1
    const val COMMUNITY_NAME_MAX = 256

    fun isValidCommunityName(name: String): Boolean =
        name.length in COMMUNITY_NAME_MIN..COMMUNITY_NAME_MAX && name.isNotBlank()

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

    /**
     * Document uploads are an allowlist, like every other media kind. `uploadDocument` checked size
     * and nothing else (#287).
     *
     * **What this is actually defending against.** Media is served over presigned URLs from the
     * media host, with the content type the uploader supplied. So a stored `text/html` opens as a
     * *page* on that origin, and `image/svg+xml` is markup that can carry script — stored XSS
     * against anyone who follows the link. Both are deliberately absent, and so is anything else
     * a browser will execute.
     *
     * **`application/octet-stream` is on the list on purpose, and it is not a hole.** Android's
     * `contentResolver.getType()` returns it whenever the system cannot identify a file, so
     * refusing it would reject perfectly ordinary documents — that is the "needs client changes"
     * half of #287. It is safe to accept precisely because it is the type browsers *download*
     * rather than render: it cannot be the XSS vector this list exists to close.
     *
     * Deliberately not exhaustive. A type nobody has asked for yet is one line to add; a type that
     * renders is a vulnerability, so the default has to be "no".
     */
    val ALLOWED_DOCUMENT_TYPES = setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.oasis.opendocument.presentation",
        "application/rtf",
        "application/zip",
        "application/octet-stream",
        "text/plain",
        "text/csv"
    )

    fun isAllowedDocumentType(contentType: String): Boolean =
        // Split on ';' so "text/plain; charset=utf-8" — which is what a real client sends — is
        // matched on its type rather than rejected for carrying a parameter.
        contentType.substringBefore(';').trim().lowercase() in ALLOWED_DOCUMENT_TYPES

    // Reactions
    //
    // An allow-list rather than a length or grapheme rule, because the client has no free-emoji
    // picker: the reaction bar offers exactly these six and there is no other route to reacting.
    // So the server can enforce precisely what the UI is able to produce — neither narrower nor
    // wider. Until #557 it enforced nothing at all, and `reactions.emoji` is VARCHAR(16), so any
    // sixteen characters could be pushed over WebSocket into a conversation.
    //
    // This is the single source of truth; the reaction bar reads it, so adding a seventh reaction
    // is one edit rather than two that can drift apart. The exact code points are asserted in
    // ValidationRulesTest — the heart is U+2764 U+FE0F and that variation selector is invisible
    // here, so a test rather than a comment is what keeps it from being lost in an edit.
    val ALLOWED_REACTIONS: Set<String> = setOf(
        "❤️",
        "👍",
        "😂",
        "😮",
        "😢",
        "🙏"
    )

    fun isValidReaction(emoji: String): Boolean = emoji in ALLOWED_REACTIONS
}
