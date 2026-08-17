package com.muhabbet.app.ui.settings

/**
 * The languages the app ships strings for.
 *
 * [code] is three things at once: the `values-<code>` suffix under `composeResources`, the value
 * `TokenStorage.setLanguage` persists, and what `MainActivity` hands to `Configuration.setLocale`.
 * It lives on the enum so those three are declared once — the language picker used to spell them
 * out as string literals in its option list and then default to a separate `"tr"` constant, and
 * that constant is what made the radio lie (#505).
 */
internal enum class AppLanguage(val code: String) {
    Turkish("tr"),
    English("en")
}

/**
 * Which language row is filled in.
 *
 * [stored] is the saved preference, if the user has ever made a choice; [rendered] is the language
 * the resource system actually resolved for this composition, read out of `strings.xml` itself so
 * that it accounts for the fallback to `values/` on a device locale the app does not translate.
 *
 * Neither may be replaced by a constant, which is the whole of the fix: on a fresh install on an
 * `en-US` device there is no stored preference at all, and answering with the project's default
 * locale marked **Türkçe** selected while every string on screen was English. The consequence was
 * worse than a mismatch — a Turkish user on an English device could not choose Turkish, because it
 * already looked chosen and tapping it did nothing.
 *
 * The preference wins when it names a language the app offers, because it is what the next launch
 * will apply; otherwise the answer is whatever is on screen right now. The final fallback is
 * unreachable while `app_language_code` is declared in both locales, but a total function needs an
 * answer and `values/` is the default locale.
 */
internal fun selectedLanguage(stored: String?, rendered: String): AppLanguage =
    AppLanguage.entries.firstOrNull { it.code == stored }
        ?: AppLanguage.entries.firstOrNull { it.code == rendered }
        ?: AppLanguage.Turkish
