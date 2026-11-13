package com.muhabbet.shared.exception

import org.springframework.http.HttpStatus

/**
 * What the backend does with each wire [ErrorCode]: the HTTP status it answers with, and the
 * operator-facing default message it logs and puts in `error.message`.
 *
 * The catalogue of codes itself lives in the shared KMP module (#883), because the app has to match
 * on it and a name is only a contract if both sides compile against the same declaration. The HTTP
 * status does not belong there — it is a transport detail of this server, and the app must never
 * branch on it — so it is attached here, in the adapter layer, by an exhaustive `when`. Adding a code
 * to the shared enum therefore fails this file's compilation until somebody decides its status: there
 * is no default to fall into.
 *
 * `defaultMessage` is Turkish operator text. Clients localise the **code**; the message is a
 * diagnostic, which is why the WebSocket ack no longer carries it at all.
 */
data class ErrorSpec(val httpStatus: HttpStatus, val defaultMessage: String)

val ErrorCode.httpStatus: HttpStatus get() = spec().httpStatus

val ErrorCode.defaultMessage: String get() = spec().defaultMessage

@Suppress("CyclomaticComplexMethod", "LongMethod")
fun ErrorCode.spec(): ErrorSpec = when (this) {
    // Auth
    ErrorCode.AUTH_INVALID_PHONE -> ErrorSpec(HttpStatus.BAD_REQUEST, "Geçersiz telefon numarası")
    ErrorCode.AUTH_OTP_COOLDOWN -> ErrorSpec(HttpStatus.TOO_MANY_REQUESTS, "Lütfen yeni kod talep etmeden önce bekleyin")
    ErrorCode.AUTH_OTP_RATE_LIMIT -> ErrorSpec(HttpStatus.TOO_MANY_REQUESTS, "Çok fazla OTP talebi")
    ErrorCode.AUTH_OTP_INVALID -> ErrorSpec(HttpStatus.UNAUTHORIZED, "Geçersiz doğrulama kodu")
    ErrorCode.AUTH_OTP_EXPIRED -> ErrorSpec(HttpStatus.UNAUTHORIZED, "Doğrulama kodu süresi doldu")
    ErrorCode.AUTH_OTP_MAX_ATTEMPTS -> ErrorSpec(HttpStatus.UNAUTHORIZED, "Maksimum deneme sayısı aşıldı")
    ErrorCode.AUTH_TOKEN_INVALID -> ErrorSpec(HttpStatus.UNAUTHORIZED, "Geçersiz token")
    ErrorCode.AUTH_TOKEN_EXPIRED -> ErrorSpec(HttpStatus.UNAUTHORIZED, "Token süresi doldu")
    ErrorCode.AUTH_TOKEN_REVOKED -> ErrorSpec(HttpStatus.UNAUTHORIZED, "Token iptal edildi")
    ErrorCode.AUTH_UNAUTHORIZED -> ErrorSpec(HttpStatus.UNAUTHORIZED, "Yetkilendirme gerekli")

    // Messaging
    ErrorCode.MSG_CONVERSATION_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Konuşma bulunamadı")
    ErrorCode.MSG_NOT_MEMBER -> ErrorSpec(HttpStatus.FORBIDDEN, "Bu konuşmanın üyesi değilsiniz")
    ErrorCode.MSG_CONTENT_TOO_LONG -> ErrorSpec(HttpStatus.BAD_REQUEST, "Mesaj çok uzun")
    ErrorCode.MSG_EMPTY_CONTENT -> ErrorSpec(HttpStatus.BAD_REQUEST, "Mesaj boş olamaz")
    ErrorCode.MSG_DUPLICATE -> ErrorSpec(HttpStatus.CONFLICT, "Bu mesaj zaten işlendi")

    // Conversation
    ErrorCode.CONV_ALREADY_EXISTS -> ErrorSpec(HttpStatus.CONFLICT, "Bu kullanıcıyla zaten bir konuşma mevcut")
    ErrorCode.CONV_INVALID_PARTICIPANTS -> ErrorSpec(HttpStatus.BAD_REQUEST, "Geçersiz katılımcı")
    ErrorCode.CONV_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Konuşma bulunamadı")
    ErrorCode.CONV_MAX_MEMBERS -> ErrorSpec(HttpStatus.BAD_REQUEST, "Grup maksimum üye sayısına ulaştı")

    // Media
    ErrorCode.MEDIA_TOO_LARGE -> ErrorSpec(HttpStatus.PAYLOAD_TOO_LARGE, "Dosya boyutu çok büyük")
    ErrorCode.MEDIA_UNSUPPORTED_TYPE -> ErrorSpec(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Desteklenmeyen dosya türü")
    ErrorCode.MEDIA_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Dosya bulunamadı veya süresi dolmuş")
    ErrorCode.MEDIA_UPLOAD_FAILED -> ErrorSpec(HttpStatus.INTERNAL_SERVER_ERROR, "Dosya yükleme başarısız")
    ErrorCode.MEDIA_FORBIDDEN -> ErrorSpec(HttpStatus.FORBIDDEN, "Bu dosyaya erişim yetkiniz yok")

    // Contacts
    ErrorCode.CONTACT_SYNC_LIMIT_EXCEEDED -> ErrorSpec(HttpStatus.BAD_REQUEST, "Tek seferde en fazla 1000 kişi senkronize edilebilir")

    // Group
    ErrorCode.GROUP_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Grup bulunamadı")
    ErrorCode.GROUP_NOT_MEMBER -> ErrorSpec(HttpStatus.FORBIDDEN, "Bu grubun üyesi değilsiniz")
    ErrorCode.GROUP_PERMISSION_DENIED -> ErrorSpec(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz yok")
    ErrorCode.GROUP_ALREADY_MEMBER -> ErrorSpec(HttpStatus.CONFLICT, "Kullanıcı zaten grup üyesi")
    ErrorCode.GROUP_CANNOT_REMOVE_OWNER -> ErrorSpec(HttpStatus.BAD_REQUEST, "Grup sahibi çıkarılamaz")
    ErrorCode.GROUP_OWNER_CANNOT_LEAVE -> ErrorSpec(HttpStatus.BAD_REQUEST, "Grup sahibi gruptan ayrılamaz, önce sahipliği devredin")
    ErrorCode.GROUP_CANNOT_MODIFY_DIRECT -> ErrorSpec(HttpStatus.BAD_REQUEST, "Direkt mesajlaşma grupları değiştirilemez")

    // Message management
    ErrorCode.MSG_NOT_SENDER -> ErrorSpec(HttpStatus.FORBIDDEN, "Sadece gönderen mesajı düzenleyebilir veya silebilir")
    ErrorCode.MSG_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Mesaj bulunamadı")
    ErrorCode.MSG_ALREADY_DELETED -> ErrorSpec(HttpStatus.CONFLICT, "Mesaj zaten silinmiş")
    ErrorCode.MSG_EDIT_WINDOW_EXPIRED -> ErrorSpec(HttpStatus.BAD_REQUEST, "Mesaj düzenleme süresi doldu")
    ErrorCode.MSG_INVALID_CURSOR -> ErrorSpec(HttpStatus.BAD_REQUEST, "Geçersiz zaman damgası formatı")
    ErrorCode.MSG_INVALID_REACTION -> ErrorSpec(HttpStatus.BAD_REQUEST, "Geçersiz tepki")

    // Status
    ErrorCode.STATUS_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Durum bulunamadı")

    // Channel
    ErrorCode.CHANNEL_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Kanal bulunamadı")
    ErrorCode.CHANNEL_NOT_A_CHANNEL -> ErrorSpec(HttpStatus.BAD_REQUEST, "Bu konuşma bir kanal değil")

    // Poll
    ErrorCode.POLL_MESSAGE_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Anket mesajı bulunamadı")
    ErrorCode.POLL_INVALID_OPTION -> ErrorSpec(HttpStatus.BAD_REQUEST, "Geçersiz anket seçeneği")

    // Chat wallpaper. An unrecognised type used to reach Enum.valueOf and come back as a 500 for
    // what is a malformed request body — latent until #380 gave the enum a fourth value.
    ErrorCode.WALLPAPER_INVALID_TYPE -> ErrorSpec(HttpStatus.BAD_REQUEST, "Geçersiz sohbet arka planı türü")

    // Encryption
    ErrorCode.ENCRYPTION_KEY_BUNDLE_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Şifreleme anahtar paketi bulunamadı")
    ErrorCode.ENCRYPTION_INVALID_KEY_DATA -> ErrorSpec(HttpStatus.BAD_REQUEST, "Geçersiz şifreleme anahtar verisi")

    // Call
    ErrorCode.CALL_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Arama bulunamadı")
    ErrorCode.CALL_USER_BUSY -> ErrorSpec(HttpStatus.CONFLICT, "Kullanıcı zaten bir aramada")
    ErrorCode.CALL_INVALID_TARGET -> ErrorSpec(HttpStatus.BAD_REQUEST, "Geçersiz arama hedefi")

    // User / KVKK
    ErrorCode.USER_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı")
    ErrorCode.USER_ALREADY_DELETED -> ErrorSpec(HttpStatus.CONFLICT, "Hesap zaten silinmiş")

    // Moderation
    ErrorCode.REPORT_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Rapor bulunamadı")
    ErrorCode.BLOCK_SELF -> ErrorSpec(HttpStatus.BAD_REQUEST, "Kendinizi engelleyemezsiniz")

    // Bot
    ErrorCode.BOT_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Bot bulunamadı")
    ErrorCode.BOT_INACTIVE -> ErrorSpec(HttpStatus.FORBIDDEN, "Bot devre dışı")

    // Backup
    ErrorCode.BACKUP_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Yedek bulunamadı")
    ErrorCode.BACKUP_IN_PROGRESS -> ErrorSpec(HttpStatus.CONFLICT, "Yedekleme zaten devam ediyor")

    // Two-Step Verification (2FA)
    ErrorCode.AUTH_2FA_PIN_REQUIRED -> ErrorSpec(HttpStatus.FORBIDDEN, "İki adımlı doğrulama PIN'i gerekli")
    ErrorCode.AUTH_2FA_PIN_INVALID -> ErrorSpec(HttpStatus.UNAUTHORIZED, "Geçersiz iki adımlı doğrulama PIN'i")
    ErrorCode.AUTH_2FA_ALREADY_ENABLED -> ErrorSpec(HttpStatus.CONFLICT, "İki adımlı doğrulama zaten etkin")
    ErrorCode.AUTH_2FA_NOT_ENABLED -> ErrorSpec(HttpStatus.BAD_REQUEST, "İki adımlı doğrulama etkin değil")

    /**
     * Too many wrong PINs; the second factor is locked for a while (#566).
     *
     * 429 rather than 401 so it cannot be mistaken for "that PIN is wrong" by anything counting
     * failures — including the client, which must stop resending rather than let the user burn
     * through the lock window one guess at a time.
     */
    ErrorCode.AUTH_2FA_LOCKED -> ErrorSpec(HttpStatus.TOO_MANY_REQUESTS, "Çok fazla hatalı PIN denemesi, lütfen bekleyin")

    // Invite Links
    ErrorCode.INVITE_LINK_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Davet bağlantısı bulunamadı")
    ErrorCode.INVITE_LINK_EXPIRED -> ErrorSpec(HttpStatus.GONE, "Davet bağlantısının süresi dolmuş")
    ErrorCode.INVITE_LINK_MAX_USES -> ErrorSpec(HttpStatus.CONFLICT, "Davet bağlantısı maksimum kullanım sayısına ulaştı")

    // Join Requests
    ErrorCode.JOIN_REQUEST_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Katılma isteği bulunamadı")
    ErrorCode.JOIN_REQUEST_ALREADY_EXISTS -> ErrorSpec(HttpStatus.CONFLICT, "Katılma isteği zaten mevcut")

    // Communities
    ErrorCode.COMMUNITY_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Topluluk bulunamadı")
    ErrorCode.COMMUNITY_PERMISSION_DENIED -> ErrorSpec(HttpStatus.FORBIDDEN, "Bu topluluk işlemi için yetkiniz yok")
    ErrorCode.COMMUNITY_NOT_A_GROUP -> ErrorSpec(HttpStatus.BAD_REQUEST, "Bu konuşma bir grup değil")
    ErrorCode.COMMUNITY_MEMBER_NOT_IN_ANY_GROUP -> ErrorSpec(
        HttpStatus.FORBIDDEN,
        "Yalnızca topluluğun gruplarında bulunan kullanıcılar topluluğa eklenebilir"
    )
    ErrorCode.COMMUNITY_INVALID_NAME -> ErrorSpec(HttpStatus.BAD_REQUEST, "Geçersiz topluluk adı")

    // #446: names are unique per creator, not globally — two different people may both call a
    // community "Aile". 409 rather than 400: the request is well-formed and would have been
    // accepted a moment earlier, which is also what tells the client to offer a different name
    // rather than to report that the field is malformed.
    ErrorCode.COMMUNITY_NAME_ALREADY_EXISTS -> ErrorSpec(HttpStatus.CONFLICT, "Bu isimde bir topluluğunuz zaten var")

    // Leaving would strand the community with no member and no owner. #407 added a real delete
    // endpoint (owner only) as the honest way to remove one; leave still refuses for a sole member
    // so the escape hatch is an explicit, auditable delete rather than an implicit one on the way out.
    ErrorCode.COMMUNITY_LAST_MEMBER_CANNOT_LEAVE -> ErrorSpec(
        HttpStatus.BAD_REQUEST,
        "Topluluğun tek üyesi ayrılamaz, önce başka birini ekleyin"
    )

    // Events
    ErrorCode.EVENT_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı")

    // View-Once & Announcement
    ErrorCode.MSG_VIEW_ONCE_ALREADY_VIEWED -> ErrorSpec(HttpStatus.CONFLICT, "Bu mesaj zaten görüntülendi")
    ErrorCode.MSG_PIN_LIMIT_REACHED -> ErrorSpec(HttpStatus.CONFLICT, "Sabitlenmiş mesaj sınırına ulaşıldı")
    ErrorCode.MSG_ANNOUNCEMENT_ONLY -> ErrorSpec(HttpStatus.FORBIDDEN, "Bu grupta sadece yöneticiler mesaj gönderebilir")

    // A message or status named a media address this server does not serve (#679). BAD_REQUEST, not
    // FORBIDDEN: nothing about the sender's permissions is wrong, the attachment itself is not one
    // this application can hand to a recipient's device. Deliberately not 401 — an ErrorCode mapped
    // to 401 is replayed by the client's Ktor auth plugin and arrives twice (#400).
    ErrorCode.MSG_MEDIA_NOT_ACCESSIBLE -> ErrorSpec(HttpStatus.BAD_REQUEST, "Medya adresi bu sunucuya ait değil")

    // Broadcast Lists
    ErrorCode.BROADCAST_LIST_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Yayın listesi bulunamadı")

    // Chat Folders (custom lists)
    ErrorCode.CHAT_FOLDER_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Klasör bulunamadı")
    ErrorCode.CHAT_FOLDER_LIMIT_REACHED -> ErrorSpec(HttpStatus.CONFLICT, "Klasör sınırına ulaşıldı")
    ErrorCode.CHAT_FOLDER_NAME_INVALID -> ErrorSpec(HttpStatus.BAD_REQUEST, "Geçersiz klasör adı")

    // Wallpaper
    ErrorCode.WALLPAPER_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Duvar kağıdı bulunamadı")

    // Login Approval
    ErrorCode.LOGIN_APPROVAL_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Giriş onayı bulunamadı")
    ErrorCode.LOGIN_APPROVAL_EXPIRED -> ErrorSpec(HttpStatus.GONE, "Giriş onayının süresi dolmuş")

    // Multi-device linking (Tier 2)
    ErrorCode.DEVICE_LINK_TOKEN_INVALID -> ErrorSpec(HttpStatus.NOT_FOUND, "Geçersiz veya bulunamayan bağlantı kodu")
    ErrorCode.DEVICE_LINK_EXPIRED -> ErrorSpec(HttpStatus.GONE, "Cihaz bağlama kodunun süresi dolmuş")
    ErrorCode.DEVICE_LINK_ALREADY_USED -> ErrorSpec(HttpStatus.CONFLICT, "Bu bağlantı kodu zaten kullanıldı")
    ErrorCode.DEVICE_LINK_LIMIT_REACHED -> ErrorSpec(HttpStatus.CONFLICT, "Bağlı cihaz sınırına ulaşıldı")
    ErrorCode.DEVICE_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Cihaz bulunamadı")
    ErrorCode.DEVICE_CANNOT_REVOKE_PRIMARY -> ErrorSpec(HttpStatus.BAD_REQUEST, "Birincil cihaz buradan kaldırılamaz")
    ErrorCode.DEVICE_LINKING_DISABLED -> ErrorSpec(HttpStatus.FORBIDDEN, "Çoklu cihaz özelliği etkin değil")

    // General
    ErrorCode.VALIDATION_ERROR -> ErrorSpec(HttpStatus.BAD_REQUEST, "Doğrulama hatası")
    ErrorCode.INTERNAL_ERROR -> ErrorSpec(HttpStatus.INTERNAL_SERVER_ERROR, "Beklenmeyen bir hata oluştu")
    ErrorCode.RATE_LIMITED -> ErrorSpec(HttpStatus.TOO_MANY_REQUESTS, "Çok fazla istek")

    // Protocol-level client mistakes. These are the shapes Spring MVC rejects before a controller
    // is ever reached; each needs a code of its own so the client can tell "you called this wrong"
    // apart from INTERNAL_ERROR, which means "the server broke, a retry may help".
    ErrorCode.ENDPOINT_NOT_FOUND -> ErrorSpec(HttpStatus.NOT_FOUND, "Böyle bir adres yok")
    ErrorCode.METHOD_NOT_ALLOWED -> ErrorSpec(HttpStatus.METHOD_NOT_ALLOWED, "Bu adres bu HTTP metodunu desteklemiyor")

    // Distinct from MEDIA_UNSUPPORTED_TYPE, which is about the *file* someone uploaded. This one is
    // about the request's own Content-Type — e.g. JSON posted to a multipart-only endpoint.
    ErrorCode.UNSUPPORTED_CONTENT_TYPE -> ErrorSpec(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Desteklenmeyen içerik türü")
    ErrorCode.NOT_ACCEPTABLE -> ErrorSpec(HttpStatus.NOT_ACCEPTABLE, "İstenen içerik türü sunulamıyor")

    // The client speaks a protocol older than `muhabbet.protocol.min-supported` (#873). 426 is the
    // status HTTP defines for exactly this; the app maps the code to its force-update screen. Not 401
    // and not 400: nothing about the request is malformed and nothing about the token is wrong.
    ErrorCode.CLIENT_UPGRADE_REQUIRED -> ErrorSpec(HttpStatus.UPGRADE_REQUIRED, "Uygulamanın güncellenmesi gerekiyor")

    // A code this build of the server does not know cannot be produced by it; the constant exists
    // for readers of the shared catalogue. Answering with it would be a server fault.
    ErrorCode.UNKNOWN -> ErrorSpec(HttpStatus.INTERNAL_SERVER_ERROR, "Beklenmeyen bir hata oluştu")
}
