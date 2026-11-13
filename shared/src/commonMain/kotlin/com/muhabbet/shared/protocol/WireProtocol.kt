package com.muhabbet.shared.protocol

/**
 * Protocol versioning between the app and the server (#873).
 *
 * The two are separate binaries that are never deployed together: the server changes on every
 * deploy, the app on a phone is whatever the user last installed from Play. Before this, nothing on
 * the wire said which protocol a client spoke, so the server could not refuse — or force an upgrade
 * on — a client too old to be safe, and could not even count which ones were still connected.
 *
 * ## Compatibility rules
 * - **Additive changes only.** A new frame, field or enum constant is fine: unknown frames decode to
 *   [WsMessage.Unknown], unknown fields are ignored, unknown enum names decode to `UNKNOWN` (#875).
 * - **Never rename a `@SerialName`** or an enum's wire name; that is a breaking change.
 * - A breaking change bumps [VERSION]. The server keeps answering older versions until
 *   `muhabbet.protocol.min-supported` is raised past them, at which point they are refused with
 *   `CLIENT_UPGRADE_REQUIRED` and the app shows its force-update screen.
 */
object WireProtocol {

    /**
     * The protocol this build speaks.
     *
     * 1 — the first versioned protocol: the client announces itself, typing travels as
     * `chat.activity` rather than inside `presence.update` (#885).
     */
    const val VERSION: Int = 1

    /**
     * What a client that announces nothing is taken to speak: every build released before
     * versioning existed. The server keeps serving it the frames it understands.
     */
    const val LEGACY_VERSION: Int = 0

    /** REST request header carrying [ClientInfo.toHeaderValue]. */
    const val CLIENT_HEADER: String = "X-Muhabbet-Client"

    /** WebSocket handshake query parameters (a browser WebSocket cannot set headers). */
    const val QUERY_PROTOCOL: String = "pv"
    const val QUERY_APP_VERSION: String = "app"
    const val QUERY_PLATFORM: String = "platform"

    /**
     * WebSocket close code for "your protocol is no longer supported" — the 4000–4999 range is the
     * one RFC 6455 leaves to applications, and 4426 mirrors HTTP 426 Upgrade Required.
     */
    const val CLOSE_UPGRADE_REQUIRED: Int = 4426
}

/**
 * Who is calling: which protocol, which app build, which platform.
 *
 * One value type with one text form, so the REST header and the WebSocket query parameters cannot
 * describe the same client differently.
 */
data class ClientInfo(
    val protocolVersion: Int,
    val appVersion: String? = null,
    val platform: String? = null,
) {

    /** `protocol=1;app=0.4.2;platform=android` — the value of [WireProtocol.CLIENT_HEADER]. */
    fun toHeaderValue(): String = buildList {
        add("$KEY_PROTOCOL=$protocolVersion")
        appVersion?.let { add("$KEY_APP=${sanitize(it)}") }
        platform?.let { add("$KEY_PLATFORM=${sanitize(it)}") }
    }.joinToString(SEPARATOR)

    companion object {
        private const val KEY_PROTOCOL = "protocol"
        private const val KEY_APP = "app"
        private const val KEY_PLATFORM = "platform"
        private const val SEPARATOR = ";"

        /** Longest app version or platform kept. They end up in a log line, so they are bounded. */
        private const val MAX_FIELD_LENGTH = 32

        /** The client a request without any announcement is: a build that predates versioning. */
        val LEGACY = ClientInfo(protocolVersion = WireProtocol.LEGACY_VERSION)

        /**
         * Parses a [toHeaderValue] string. Absent or unreadable means [LEGACY] — a header this
         * server cannot read is not a reason to refuse a request, only a reason to assume the oldest.
         */
        fun parseHeader(value: String?): ClientInfo {
            if (value.isNullOrBlank()) return LEGACY
            val fields = value.split(SEPARATOR).mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) null else part.substring(0, eq).trim() to part.substring(eq + 1).trim()
            }.toMap()
            return of(fields[KEY_PROTOCOL], fields[KEY_APP], fields[KEY_PLATFORM])
        }

        /** Builds from the three WebSocket query parameters, with the same leniency as [parseHeader]. */
        fun of(protocol: String?, appVersion: String?, platform: String?): ClientInfo {
            val version = protocol?.toIntOrNull()?.takeIf { it >= 0 } ?: return LEGACY
            return ClientInfo(
                protocolVersion = version,
                appVersion = appVersion?.let(::sanitize)?.ifBlank { null },
                platform = platform?.let(::sanitize)?.ifBlank { null },
            )
        }

        /** Keeps a client-supplied token to a short run of harmless characters before it is logged. */
        private fun sanitize(raw: String): String =
            raw.filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }.take(MAX_FIELD_LENGTH)
    }
}
