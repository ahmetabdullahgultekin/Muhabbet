package com.muhabbet.shared.api

/**
 * REST paths that both the Spring controllers and the Ktor client spell from the same constant.
 *
 * Why this exists (#780): the KVKK data-export and account-deletion buttons called
 * `/api/v1/users/data/export` and `/api/v1/users/data/account`, while the server had moved them to
 * `/api/v1/users/me/data-export` and `DELETE /api/v1/users/me`. Both sides were written by hand,
 * nothing compared them, and both buttons answered 404 on every tap — on the two rights (access
 * and erasure) the app's KVKK claim rests on.
 *
 * A constant shared by the mapping annotation and the client call makes that drift a compile error
 * instead of a 404. It does not replace the tests that spell the path as a literal on each side:
 * a test that reads the constant agrees with whatever the constant says, so the literal is what
 * pins the contract to the URL actually on the wire.
 *
 * `const val`, because a Spring mapping annotation only accepts compile-time constants. Add a route
 * here when a client/server pair is touched, rather than migrating every path in one sweep.
 */
object ApiRoutes {

    /** The signed-in user's own account (`UserDataController`). */
    object UserData {
        /** Base of the controller; also the account itself, so `DELETE` here erases it. */
        const val ME = "/api/v1/users/me"

        /** Relative to [ME], for the controller's `@GetMapping`. */
        const val DATA_EXPORT_SEGMENT = "/data-export"

        /** `GET` — one page of the KVKK m.11 export; walk `messages`/`mediaFiles` cursors to the end. */
        const val DATA_EXPORT = ME + DATA_EXPORT_SEGMENT

        /** `DELETE` — KVKK m.7 erasure of the signed-in account. */
        const val ACCOUNT = ME

        const val PARAM_MESSAGES_CURSOR = "messagesCursor"
        const val PARAM_MEDIA_CURSOR = "mediaCursor"
        const val PARAM_PAGE_SIZE = "pageSize"
    }
}
