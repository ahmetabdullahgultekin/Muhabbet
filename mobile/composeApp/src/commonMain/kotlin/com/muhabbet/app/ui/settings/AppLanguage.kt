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
 * Which language row is filled in: **the one the app is rendering**.
 *
 * [rendered] is read out of `strings.xml` itself, so it is the language of the words next to the
 * radio, fallback to `values/` included. [stored] is the saved preference — what the *next* launch
 * will try to apply, which is not the same question.
 *
 * The two can disagree, and #548 is what that looks like. The app applies the stored language by
 * writing a process-global default locale in `MainActivity.onCreate`; Android owns that global and
 * re-asserts it on its own configuration updates, so it can end up back at the device's language
 * while the preference still says Turkish. #535 answered that case with [stored], which produced
 * the reported screen exactly: every string English, the radio saying Türkçe.
 *
 * Worse than the mismatch, it closed the way out. `LanguageSection` skips a tap on the row that is
 * already selected, so the user was told Turkish was chosen, tapped Turkish, and nothing happened —
 * the same trap #505 described, arriving from the other side. Answering with what is rendering
 * makes the disagreement visible AND leaves the other row tappable, which is the whole repair.
 *
 * The final fallback is unreachable while `app_language_code` is declared in both locales, but a
 * total function needs an answer and `values/` is the default locale.
 */
internal fun selectedLanguage(stored: String?, rendered: String): AppLanguage =
    AppLanguage.entries.firstOrNull { it.code == rendered }
        ?: AppLanguage.entries.firstOrNull { it.code == stored }
        ?: AppLanguage.Turkish

/**
 * Whether choosing [language] has to restart the Activity to take effect.
 *
 * Only when it is not already the language on screen. On Android the locale is applied in
 * `MainActivity.onCreate`, so nothing below that point can apply it in place — but restarting the
 * app to arrive at the same screen in the same language is a jolt with no result to show for it.
 *
 * Deliberately compared against [rendered] and not against the stored preference: when the two have
 * drifted apart, re-picking the language that is already stored is precisely the action that has to
 * work, because it is the only thing that puts the app back into the language the user asked for.
 */
internal fun languageNeedsRestart(language: AppLanguage, rendered: String): Boolean =
    language.code != rendered
