package com.muhabbet.shared.exception

import com.muhabbet.shared.model.LenientEnumSerializer
import kotlinx.serialization.Serializable

/**
 * Every error code the server can put on the wire — `error.code` in a REST envelope,
 * `errorCode` on a WebSocket ack, `code` on an `error` frame (#883).
 *
 * It used to be a backend-only enum coupled to `HttpStatus`, so only its *name* reached the wire and
 * the app matched string literals against it. Renaming a code on the server silently turned a
 * specific, localised message on the phone into a generic one, and nothing could notice. Now both
 * sides compile against this declaration: the backend attaches its HTTP status in an exhaustive
 * mapping of its own (`ErrorSpec.kt`), and the app maps codes to strings with an exhaustive `when`.
 *
 * Framework-free on purpose — names only. Lenient like every wire enum (#875): a code added by a
 * newer server decodes to [UNKNOWN], which the app presents as a generic failure, instead of failing
 * the whole envelope.
 *
 * **Same package as the backend's `BusinessException`** (`com.muhabbet.shared.exception`) — a split
 * package across the two artifacts, chosen so the eighty-odd backend files that import
 * `com.muhabbet.shared.exception.ErrorCode` kept compiling unchanged when the enum moved here. Neither
 * side uses JPMS, where a split package would matter.
 *
 * **Client-local failures are not error codes** and must not be added here: a failure the app
 * detects itself (an upload that never started, a request that never left the phone) is not a thing
 * the server said.
 */
@Serializable(with = ErrorCodeSerializer::class)
enum class ErrorCode {
    AUTH_INVALID_PHONE,
    AUTH_OTP_COOLDOWN,
    AUTH_OTP_RATE_LIMIT,
    AUTH_OTP_INVALID,
    AUTH_OTP_EXPIRED,
    AUTH_OTP_MAX_ATTEMPTS,
    AUTH_TOKEN_INVALID,
    AUTH_TOKEN_EXPIRED,
    AUTH_TOKEN_REVOKED,
    AUTH_UNAUTHORIZED,
    MSG_CONVERSATION_NOT_FOUND,
    MSG_NOT_MEMBER,
    MSG_CONTENT_TOO_LONG,
    MSG_EMPTY_CONTENT,
    MSG_DUPLICATE,
    CONV_ALREADY_EXISTS,
    CONV_INVALID_PARTICIPANTS,
    CONV_NOT_FOUND,
    CONV_MAX_MEMBERS,
    MEDIA_TOO_LARGE,
    MEDIA_UNSUPPORTED_TYPE,
    MEDIA_NOT_FOUND,
    MEDIA_UPLOAD_FAILED,
    MEDIA_FORBIDDEN,
    CONTACT_SYNC_LIMIT_EXCEEDED,
    GROUP_NOT_FOUND,
    GROUP_NOT_MEMBER,
    GROUP_PERMISSION_DENIED,
    GROUP_ALREADY_MEMBER,
    GROUP_CANNOT_REMOVE_OWNER,
    GROUP_OWNER_CANNOT_LEAVE,
    GROUP_CANNOT_MODIFY_DIRECT,
    MSG_NOT_SENDER,
    MSG_NOT_FOUND,
    MSG_ALREADY_DELETED,
    MSG_EDIT_WINDOW_EXPIRED,
    MSG_INVALID_CURSOR,
    MSG_INVALID_REACTION,
    STATUS_NOT_FOUND,
    CHANNEL_NOT_FOUND,
    CHANNEL_NOT_A_CHANNEL,
    POLL_MESSAGE_NOT_FOUND,
    POLL_INVALID_OPTION,
    WALLPAPER_INVALID_TYPE,
    ENCRYPTION_KEY_BUNDLE_NOT_FOUND,
    ENCRYPTION_INVALID_KEY_DATA,
    CALL_NOT_FOUND,
    CALL_USER_BUSY,
    CALL_INVALID_TARGET,
    USER_NOT_FOUND,
    USER_ALREADY_DELETED,
    REPORT_NOT_FOUND,
    BLOCK_SELF,
    BOT_NOT_FOUND,
    BOT_INACTIVE,
    BACKUP_NOT_FOUND,
    BACKUP_IN_PROGRESS,
    AUTH_2FA_PIN_REQUIRED,
    AUTH_2FA_PIN_INVALID,
    AUTH_2FA_ALREADY_ENABLED,
    AUTH_2FA_NOT_ENABLED,
    AUTH_2FA_LOCKED,
    INVITE_LINK_NOT_FOUND,
    INVITE_LINK_EXPIRED,
    INVITE_LINK_MAX_USES,
    JOIN_REQUEST_NOT_FOUND,
    JOIN_REQUEST_ALREADY_EXISTS,
    COMMUNITY_NOT_FOUND,
    COMMUNITY_PERMISSION_DENIED,
    COMMUNITY_NOT_A_GROUP,
    COMMUNITY_MEMBER_NOT_IN_ANY_GROUP,
    COMMUNITY_INVALID_NAME,
    COMMUNITY_NAME_ALREADY_EXISTS,
    COMMUNITY_LAST_MEMBER_CANNOT_LEAVE,
    EVENT_NOT_FOUND,
    MSG_VIEW_ONCE_ALREADY_VIEWED,
    MSG_PIN_LIMIT_REACHED,
    MSG_ANNOUNCEMENT_ONLY,
    MSG_MEDIA_NOT_ACCESSIBLE,
    BROADCAST_LIST_NOT_FOUND,
    CHAT_FOLDER_NOT_FOUND,
    CHAT_FOLDER_LIMIT_REACHED,
    CHAT_FOLDER_NAME_INVALID,
    WALLPAPER_NOT_FOUND,
    LOGIN_APPROVAL_NOT_FOUND,
    LOGIN_APPROVAL_EXPIRED,
    DEVICE_LINK_TOKEN_INVALID,
    DEVICE_LINK_EXPIRED,
    DEVICE_LINK_ALREADY_USED,
    DEVICE_LINK_LIMIT_REACHED,
    DEVICE_NOT_FOUND,
    DEVICE_CANNOT_REVOKE_PRIMARY,
    DEVICE_LINKING_DISABLED,
    VALIDATION_ERROR,
    INTERNAL_ERROR,
    RATE_LIMITED,
    ENDPOINT_NOT_FOUND,
    METHOD_NOT_ALLOWED,
    UNSUPPORTED_CONTENT_TYPE,
    NOT_ACCEPTABLE,

    /**
     * This client's protocol is older than the server still supports (#873). REST answers it with
     * 426, the WebSocket closes with [com.muhabbet.shared.protocol.WireProtocol.CLOSE_UPGRADE_REQUIRED].
     */
    CLIENT_UPGRADE_REQUIRED,

    /** A code from a server newer than this build. */
    UNKNOWN
}

object ErrorCodeSerializer : LenientEnumSerializer<ErrorCode>("ErrorCode", ErrorCode.entries, ErrorCode.UNKNOWN)
