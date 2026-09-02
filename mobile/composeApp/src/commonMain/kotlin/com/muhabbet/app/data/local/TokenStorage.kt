package com.muhabbet.app.data.local

interface TokenStorage {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun getUserId(): String?
    fun getDeviceId(): String?
    fun saveTokens(accessToken: String, refreshToken: String, userId: String, deviceId: String)
    fun clear()
    fun isLoggedIn(): Boolean = getAccessToken() != null
    fun getLanguage(): String? = null
    fun setLanguage(lang: String) {}

    /**
     * Whether the process that is about to start should reopen Settings at the language picker.
     *
     * Set immediately before the language restart and consumed exactly once by `RootComponent` on
     * the way back up, so the user lands where they were instead of on the conversation list — which
     * is also the only place they can see that the new language took effect (#505).
     *
     * Abstract rather than defaulted, for the same reason as the theme below. An implementation that
     * inherited a no-op would restart the app and drop the user on the home screen every time, which
     * is precisely the defect this exists to fix, and it would compile.
     */
    fun setPendingLanguageRestart()
    fun consumePendingLanguageRestart(): Boolean
    // Abstract, unlike its neighbours: the theme is read at the composition root on every frame, so
    // an implementation that inherited a null-returning default would pin the whole app to the
    // system theme with nothing to show for it. Better to fail to compile.
    fun getTheme(): String?
    fun setTheme(theme: String)

    // Same reasoning as the theme: read on every frame at the composition root, so a null-returning
    // default would silently disable haptics app-wide with nothing to show for it.
    fun getHapticsEnabled(): Boolean
    fun setHapticsEnabled(enabled: Boolean)

    /**
     * Whether pressing Enter in the chat composer sends the message (#516).
     *
     * Defaults to **true** — the messenger convention, and what the issue asked for — so a device
     * that has never opened the setting behaves like WhatsApp does out of the box. The alternative
     * is not "Enter does nothing": with this off, Enter inserts a newline, which is what the field
     * did before the setting existed.
     *
     * Abstract, for the eighth time in this file and for the same reason as every neighbour that
     * says so. A defaulted no-op getter returning false would leave Enter inserting newlines on
     * every device forever while the switch in Settings appeared to move — which is precisely the
     * class of defect (#377, #378, #380, #383) the 2026-08-15 audit found a whole screen of, and it
     * would compile. Store and read, or fail to build.
     *
     * A per-device preference, deliberately not synced to the account: which key sends is a property
     * of the keyboard in front of you, and the right answer on a laptop is often the wrong one on a
     * phone.
     */
    fun getEnterToSend(): Boolean
    fun setEnterToSend(enabled: Boolean)

    fun getLastSyncTimestamp(): String? = null
    fun setLastSyncTimestamp(timestamp: String) {}

    // Abstract, unlike most of this file's neighbours and for the reason #378 exists: these two
    // were defaulted no-ops, no implementation overrode either, so the App Lock toggle in Settings
    // wrote to nothing and always read back `false`/`null` — a security setting that silently did
    // not persist, in a privacy-first messenger. A default that compiles is exactly the failure this
    // file already guards against for the theme, haptics, media quality and wallpaper fields below.
    // Stored in the *encrypted* store on Android (see AndroidTokenStorage) — this is a security
    // setting, not a cosmetic preference, even though the value itself is not secret material.
    fun getAppLockEnabled(): Boolean
    fun setAppLockEnabled(enabled: Boolean)

    /** One of [com.muhabbet.app.config.AppLockTimeout]'s option keys, or null if never chosen. */
    fun getAppLockTimeout(): String?
    fun setAppLockTimeout(timeout: String)

    // Abstract for the same reason as the theme and haptics above. These two were defaulted, no
    // implementation overrode either, and so the HD option in Settings wrote to an empty body and
    // read back null on every platform — the picker reset to "standard" each time it opened and
    // every upload compressed at the standard profile regardless. A no-op that compiles is exactly
    // the failure this file already guards against twice.
    fun getMediaQuality(): String?
    fun setMediaQuality(quality: String)

    /**
     * When the user agreed to contact matching, as an ISO-8601 instant; null means they never have.
     *
     * Abstract for the same reason as media quality above, and with more at stake. A defaulted
     * no-op would read back null forever, so the consent screen would either re-ask on every visit
     * or — worse, if the gate were written the other way round — never record a refusal and upload
     * the address book anyway. That is the one control here carrying a legal obligation (#425), so
     * it must not be possible to satisfy this interface by doing nothing.
     */
    fun getContactSyncConsentAt(): String?
    fun setContactSyncConsentAt(timestamp: String)
    fun clearContactSyncConsent()

    // Abstract for the same reason as media quality above (#380): the four original pairs below were
    // defaulted, no implementation overrode any of them, and so WallpaperPickerScreen wrote to an empty body and
    // read back null/false on every platform every time — the picker looked like it remembered a
    // choice only because that choice lived in the screen's own `remember{}` for the one composition
    // it was open. A no-op that compiles is exactly the failure this file already guards against.
    fun getWallpaperType(): String?
    fun setWallpaperType(type: String)
    fun getSolidColor(): String?
    fun setSolidColor(color: String?)

    /**
     * The **id** of the chosen gradient wallpaper (`MuhabbetWallpaperGradient.id`), not its colours.
     *
     * Separate from [getSolidColor] rather than sharing one "value" slot with it, so that switching
     * between SOLID and GRADIENT and back does not silently overwrite the other's choice — the two
     * are different value spaces (a hex, an id) and a single slot would have to guess which it holds.
     */
    fun getWallpaperGradientId(): String?
    fun setWallpaperGradientId(id: String?)
    fun getCustomWallpaperPath(): String?
    fun setCustomWallpaperPath(path: String?)
    fun getDarkModeWallpaperEnabled(): Boolean
    fun setDarkModeWallpaperEnabled(enabled: Boolean)

    /**
     * The app version whose test-build notice the user has already acknowledged, or null if they
     * never have. Compared against `BuildInfo.VERSION`, so the notice returns once after an update
     * and stays away on every launch in between.
     *
     * Abstract, for the third time in this file and for the same reason (#380, media quality,
     * contact consent): a defaulted no-op would read back null on every launch, so the notice would
     * reappear every single time the app opened — and a warning that shows up that often is one
     * people learn to dismiss without reading, which is precisely the failure it exists to avoid.
     */
    fun getTestBuildNoticeAckVersion(): String?
    fun setTestBuildNoticeAckVersion(version: String)

    /**
     * Whether the app has already put the system notification-permission dialog in front of this
     * user (#547). Written once, before the dialog is shown, and never cleared.
     *
     * Abstract, for the fourth time in this file and for the same reason (#380, media quality,
     * contact consent, the test-build notice): a defaulted no-op would read back false on every
     * launch, so the app would ask for notification permission every single time it started. Android
     * stops showing the dialog after two denials, so the visible result would not be a repeated
     * prompt — it would be a request that silently does nothing, forever, which is far harder to
     * notice than a broken one.
     *
     * Deliberately not cleared by [clear]: the permission belongs to the app, not to the session, so
     * logging out and back in is not a reason to ask again.
     */
    fun getNotificationPermissionAsked(): Boolean
    fun setNotificationPermissionAsked()

    /**
     * Whether the app has already put the system **contacts** permission dialog in front of this
     * user (#691). Written once, before the dialog is shown, and never cleared.
     *
     * This is the only thing that separates "never asked" from "asked and refused" —
     * `shouldShowRequestPermissionRationale` needs an Activity and `AndroidContactsProvider` holds
     * the application context, so the OS cannot be asked. See [ContactsAccess].
     *
     * Abstract, for the fifth time in this file and for the same reason as its four neighbours: a
     * defaulted no-op would read back false forever, so a user who has already refused twice —
     * after which Android never shows the dialog again — would keep being offered a button that
     * does nothing, instead of the settings route that still works.
     *
     * Deliberately not cleared by [clear], like the notification flag: the permission belongs to
     * the app, not to the session.
     */
    fun getContactsPermissionAsked(): Boolean
    fun setContactsPermissionAsked()

    /**
     * Whether the first-run welcome flow (#692) has been seen. Written when it is finished **or
     * skipped**, since both are the user telling us they are done with it.
     *
     * A plain boolean rather than the version string the test-build notice stores: that notice
     * returns after every update because what it warns about changes with the build, whereas an
     * introduction that reappeared on every update would be an app that keeps introducing itself.
     *
     * Abstract for the same reason as everything else in this block. A defaulted no-op would show
     * the welcome flow on every single launch — over the conversation list, before the user can
     * reach anything.
     */
    fun getWelcomeSeen(): Boolean
    fun setWelcomeSeen()

    /**
     * The app version whose release notes this user has already been shown, or null on a device
     * that has never recorded one (#672).
     *
     * Seeded once by `RootComponent` — see `versionToRecordOnFirstLaunch` for why a fresh install
     * and an upgrade from a build without this feature both arrive here as null and must not be
     * treated the same — and rewritten each time the "What's new" sheet is dismissed.
     *
     * Abstract, for the seventh time in this file and for the same reason (#380, media quality,
     * contact consent, the test-build notice, the notification prompt, the contacts prompt, the
     * welcome flow): a defaulted no-op would read
     * back null on every launch. Because null means "fresh install, say nothing", the visible result
     * would not be a sheet that repeats — it would be a sheet that never appears at all, on any
     * device, forever. That is the exact failure #672 exists to fix, and it would compile.
     *
     * Deliberately not cleared by [clear]: which releases a person has seen is a fact about the
     * install, not about the session, and logging out is not a reason to show them again.
     */
    fun getLastSeenVersion(): String?
    fun setLastSeenVersion(version: String)
}
