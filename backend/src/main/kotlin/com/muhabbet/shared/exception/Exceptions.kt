package com.muhabbet.shared.exception

import org.springframework.http.HttpStatus

/**
 * All error codes in the system. Each maps to an HTTP status.
 * These codes appear in API error responses as: { "error": { "code": "AUTH_OTP_EXPIRED", ... } }
 */
enum class ErrorCode(val httpStatus: HttpStatus, val defaultMessage: String) {

    // Auth
    AUTH_INVALID_PHONE(HttpStatus.BAD_REQUEST, "Geçersiz telefon numarası"),
    AUTH_OTP_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "Lütfen yeni kod talep etmeden önce bekleyin"),
    AUTH_OTP_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "Çok fazla OTP talebi"),
    AUTH_OTP_INVALID(HttpStatus.UNAUTHORIZED, "Geçersiz doğrulama kodu"),
    AUTH_OTP_EXPIRED(HttpStatus.UNAUTHORIZED, "Doğrulama kodu süresi doldu"),
    AUTH_OTP_MAX_ATTEMPTS(HttpStatus.UNAUTHORIZED, "Maksimum deneme sayısı aşıldı"),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Geçersiz token"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token süresi doldu"),
    AUTH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "Token iptal edildi"),
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Yetkilendirme gerekli"),

    // Messaging
    MSG_CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Konuşma bulunamadı"),
    MSG_NOT_MEMBER(HttpStatus.FORBIDDEN, "Bu konuşmanın üyesi değilsiniz"),
    MSG_CONTENT_TOO_LONG(HttpStatus.BAD_REQUEST, "Mesaj çok uzun"),
    MSG_EMPTY_CONTENT(HttpStatus.BAD_REQUEST, "Mesaj boş olamaz"),
    MSG_DUPLICATE(HttpStatus.CONFLICT, "Bu mesaj zaten işlendi"),

    // Conversation
    CONV_ALREADY_EXISTS(HttpStatus.CONFLICT, "Bu kullanıcıyla zaten bir konuşma mevcut"),
    CONV_INVALID_PARTICIPANTS(HttpStatus.BAD_REQUEST, "Geçersiz katılımcı"),
    CONV_NOT_FOUND(HttpStatus.NOT_FOUND, "Konuşma bulunamadı"),
    CONV_MAX_MEMBERS(HttpStatus.BAD_REQUEST, "Grup maksimum üye sayısına ulaştı"),

    // Media
    MEDIA_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Dosya boyutu çok büyük"),
    MEDIA_UNSUPPORTED_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Desteklenmeyen dosya türü"),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "Dosya bulunamadı veya süresi dolmuş"),
    MEDIA_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Dosya yükleme başarısız"),

    // Contacts
    CONTACT_SYNC_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "Tek seferde en fazla 1000 kişi senkronize edilebilir"),

    // Group
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "Grup bulunamadı"),
    GROUP_NOT_MEMBER(HttpStatus.FORBIDDEN, "Bu grubun üyesi değilsiniz"),
    GROUP_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz yok"),
    GROUP_ALREADY_MEMBER(HttpStatus.CONFLICT, "Kullanıcı zaten grup üyesi"),
    GROUP_CANNOT_REMOVE_OWNER(HttpStatus.BAD_REQUEST, "Grup sahibi çıkarılamaz"),
    GROUP_OWNER_CANNOT_LEAVE(HttpStatus.BAD_REQUEST, "Grup sahibi gruptan ayrılamaz, önce sahipliği devredin"),
    GROUP_CANNOT_MODIFY_DIRECT(HttpStatus.BAD_REQUEST, "Direkt mesajlaşma grupları değiştirilemez"),

    // Message management
    MSG_NOT_SENDER(HttpStatus.FORBIDDEN, "Sadece gönderen mesajı düzenleyebilir veya silebilir"),
    MSG_NOT_FOUND(HttpStatus.NOT_FOUND, "Mesaj bulunamadı"),
    MSG_ALREADY_DELETED(HttpStatus.CONFLICT, "Mesaj zaten silinmiş"),
    MSG_EDIT_WINDOW_EXPIRED(HttpStatus.BAD_REQUEST, "Mesaj düzenleme süresi doldu"),

    // Status
    STATUS_NOT_FOUND(HttpStatus.NOT_FOUND, "Durum bulunamadı"),

    // Channel
    CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND, "Kanal bulunamadı"),
    CHANNEL_NOT_A_CHANNEL(HttpStatus.BAD_REQUEST, "Bu konuşma bir kanal değil"),

    // Poll
    POLL_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Anket mesajı bulunamadı"),
    POLL_INVALID_OPTION(HttpStatus.BAD_REQUEST, "Geçersiz anket seçeneği"),

    // Encryption
    ENCRYPTION_KEY_BUNDLE_NOT_FOUND(HttpStatus.NOT_FOUND, "Şifreleme anahtar paketi bulunamadı"),
    ENCRYPTION_INVALID_KEY_DATA(HttpStatus.BAD_REQUEST, "Geçersiz şifreleme anahtar verisi"),

    // Call
    CALL_NOT_FOUND(HttpStatus.NOT_FOUND, "Arama bulunamadı"),
    CALL_USER_BUSY(HttpStatus.CONFLICT, "Kullanıcı zaten bir aramada"),
    CALL_INVALID_TARGET(HttpStatus.BAD_REQUEST, "Geçersiz arama hedefi"),

    // User / KVKK
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı"),
    USER_ALREADY_DELETED(HttpStatus.CONFLICT, "Hesap zaten silinmiş"),

    // Moderation
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "Rapor bulunamadı"),
    BLOCK_SELF(HttpStatus.BAD_REQUEST, "Kendinizi engelleyemezsiniz"),

    // Bot
    BOT_NOT_FOUND(HttpStatus.NOT_FOUND, "Bot bulunamadı"),
    BOT_INACTIVE(HttpStatus.FORBIDDEN, "Bot devre dışı"),

    // Backup
    BACKUP_NOT_FOUND(HttpStatus.NOT_FOUND, "Yedek bulunamadı"),
    BACKUP_IN_PROGRESS(HttpStatus.CONFLICT, "Yedekleme zaten devam ediyor"),

    // General
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Doğrulama hatası"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Beklenmeyen bir hata oluştu"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Çok fazla istek");
}

/**
 * Business exception — thrown by domain services, caught by GlobalExceptionHandler.
 */
class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.defaultMessage,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)
