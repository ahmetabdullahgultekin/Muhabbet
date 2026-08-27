package com.muhabbet.app.ui.communities

/**
 * `ErrorCode.COMMUNITY_NAME_ALREADY_EXISTS` on the backend (#446): this account already has a
 * community under the name that was sent, ignoring case, surrounding whitespace and the Turkish
 * dotted/dotless i.
 *
 * Named once here because two screens answer it the same way — the create screen and the rename
 * dialog both show it against the name field rather than as a toast. It is the wire string, not an
 * enum shared with the backend, so it has to match `ErrorCode.name` exactly; a typo would silently
 * downgrade both screens to the generic failure message they had before.
 */
internal const val CommunityNameTakenCode = "COMMUNITY_NAME_ALREADY_EXISTS"
