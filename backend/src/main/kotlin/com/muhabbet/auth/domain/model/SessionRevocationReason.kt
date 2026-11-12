package com.muhabbet.auth.domain.model

/**
 * Why the auth module is telling every live session of a device or user to end (#747).
 *
 * Carried to the transport so it can say which it was: a client told "logged out" and a client told
 * "this device was removed" should not have to guess from a bare close.
 */
enum class SessionRevocationReason {
    /** The device signed itself out. */
    LOGOUT,

    /** The owner removed this (companion) device from their account. */
    DEVICE_REVOKED,

    /** The account no longer exists. */
    ACCOUNT_DELETED
}
