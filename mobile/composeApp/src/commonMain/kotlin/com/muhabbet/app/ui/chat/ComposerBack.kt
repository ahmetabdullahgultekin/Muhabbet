package com.muhabbet.app.ui.chat

/** What back does to the composer, before it is allowed to leave the chat (#803). */
internal enum class ComposerBack {
    /** Abandon the edit and clear the field the edit filled in. */
    CancelEdit,
    /** Drop the quoted message; the typed text stays. */
    CancelReply
}

/**
 * Innermost first: an edit is cancelled before a reply, and only a composer with neither lets back
 * pop the chat (null). Before #803 back always popped it, discarding the edit or reply mid-way.
 */
internal fun composerBack(isEditing: Boolean, isReplying: Boolean): ComposerBack? = when {
    isEditing -> ComposerBack.CancelEdit
    isReplying -> ComposerBack.CancelReply
    else -> null
}
