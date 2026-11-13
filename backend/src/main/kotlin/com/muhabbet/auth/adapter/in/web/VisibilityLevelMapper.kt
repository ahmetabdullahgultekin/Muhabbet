package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.model.VisibilityLevel
import com.muhabbet.shared.model.VisibilityLevelSerializer

/**
 * The two directions between the wire [VisibilityLevel] and the lowercase string the `users` table
 * stores (`everyone` / `contacts` / `nobody`). The column predates the typed field and stores exactly
 * the wire name, so the mapping is the serializer's own table rather than a second copy of it.
 *
 * Writing is strict where it used to be absent: `PATCH /me/privacy` stored whatever string it was
 * given, so a typo became a visibility no reader recognised. A value this server cannot name — which
 * is what [VisibilityLevel.UNKNOWN] means — is now refused with `VALIDATION_ERROR` (#877).
 */
internal fun VisibilityLevel.toStored(): String {
    if (this == VisibilityLevel.UNKNOWN) throw BusinessException(ErrorCode.VALIDATION_ERROR)
    return wireName
}

/** A stored value is mapped leniently: an unreadable one is reported as UNKNOWN, not thrown. */
internal fun String.toVisibilityLevel(): VisibilityLevel = VisibilityLevelSerializer.fromWire(this)
