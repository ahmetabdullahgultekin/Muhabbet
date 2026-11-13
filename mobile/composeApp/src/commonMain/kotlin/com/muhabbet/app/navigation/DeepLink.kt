package com.muhabbet.app.navigation

/**
 * What a link into the app asks for (#815). Typed, so nothing downstream handles a raw string.
 */
sealed interface DeepLink {
    data class Chat(val conversationId: String) : DeepLink
    /** An invite link. Recognised, but there is no in-app join flow yet, so nothing acts on it. */
    data class Invite(val code: String) : DeepLink
    data object Unknown : DeepLink
}

/**
 * Parses the link shapes the manifest routes here:
 *  - `muhabbet://chat/{id}` — `chat` is the host, the id the only path segment;
 *  - `https://muhabbet.app/chat/{id}` and `https://muhabbet.app/invite/{code}` — the kind is the
 *    first segment.
 *
 * Strict on purpose. It used to take `pathSegments.lastOrNull()`, so `https://…/chat` opened a
 * conversation whose id was `"chat"`, and `muhabbet://chat/a/b` opened `b`. A conversation id is a
 * server-minted UUID; anything else is [DeepLink.Unknown] and the app opens where it always does.
 *
 * The https host is checked here as well as in the manifest: an explicit intent to an exported
 * Activity is not filtered by its intent-filters, so the manifest alone is not a boundary.
 *
 * Platform-free (scheme, host and path segments as plain strings) so it is unit-tested in common code.
 */
object DeepLinkParser {

    fun parse(scheme: String?, host: String?, pathSegments: List<String>): DeepLink {
        val segments = pathSegments.filter { it.isNotEmpty() }
        val (kind, args) = when {
            scheme.equals(CUSTOM_SCHEME, ignoreCase = true) -> host to segments
            scheme.equals(HTTPS_SCHEME, ignoreCase = true) && host.equals(APP_LINK_HOST, ignoreCase = true) ->
                segments.firstOrNull() to segments.drop(1)
            else -> return DeepLink.Unknown
        }
        val argument = args.singleOrNull() ?: return DeepLink.Unknown
        return when {
            kind.equals(CHAT, ignoreCase = true) && isUuid(argument) -> DeepLink.Chat(argument)
            kind.equals(INVITE, ignoreCase = true) && INVITE_CODE.matches(argument) -> DeepLink.Invite(argument)
            else -> DeepLink.Unknown
        }
    }

    /** Canonical 8-4-4-4-12 hex form, which is what the backend mints (`gen_random_uuid()`). */
    fun isUuid(value: String): Boolean = UUID.matches(value)

    private const val CUSTOM_SCHEME = "muhabbet"
    private const val HTTPS_SCHEME = "https"
    /** Must match the https intent-filter in AndroidManifest.xml. */
    private const val APP_LINK_HOST = "muhabbet.app"
    private const val CHAT = "chat"
    private const val INVITE = "invite"
    private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val INVITE_CODE = Regex("^[A-Za-z0-9_-]{1,64}$")
}
