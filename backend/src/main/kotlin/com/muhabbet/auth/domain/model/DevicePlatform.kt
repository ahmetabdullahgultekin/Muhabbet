package com.muhabbet.auth.domain.model

/**
 * The operating system family a device runs (#882).
 *
 * A closed set rather than the free `String` it used to be: the platform was also, wrongly, the
 * device's *identity* — sign-in looked a device up by `(userId, platform)` — and a free string let
 * any spelling through. Identity now comes from [Device.installationId]; the platform is only what
 * it says, and an unknown value is a client error rather than a new kind of device.
 *
 * [wireName] is the lowercase form every client has always sent and the `devices.platform` column
 * stores (constrained by V28's `ck_devices_platform`).
 */
enum class DevicePlatform(val wireName: String) {
    ANDROID("android"),
    IOS("ios"),
    WEB("web");

    companion object {
        /** The platform [value] names, ignoring case and surrounding blanks, or null when none does. */
        fun parse(value: String): DevicePlatform? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.wireName == normalized }
        }
    }
}
