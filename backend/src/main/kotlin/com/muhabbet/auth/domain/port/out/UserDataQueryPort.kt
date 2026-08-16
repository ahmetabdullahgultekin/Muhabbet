package com.muhabbet.auth.domain.port.out

import java.util.UUID

interface UserDataQueryPort {
    fun countMessagesByUserId(userId: UUID): Long
    fun countConversationsByUserId(userId: UUID): Long
    fun countMediaFilesByUserId(userId: UUID): Long
    fun removeUserFromAllConversations(userId: UUID)

    /**
     * Erases everything that identifies the person or makes them findable: the discovery hash,
     * devices and push tokens, encryption keys, contacts, and the per-user settings rows.
     *
     * Deliberately does **not** touch messages or media. Those sit inside other people's
     * conversations, and erasing them would delete correspondence belonging to someone who has not
     * asked for anything — the "rights of others" carve-out both KVKK and GDPR Art. 17(3) allow.
     * The sender is anonymised instead, so the words remain and the person behind them does not.
     * This choice has to be what the privacy policy says; see #426.
     */
    fun erasePersonalData(userId: UUID)
}
