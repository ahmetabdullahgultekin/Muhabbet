package com.muhabbet.app.ui.chat

/**
 * What a press of Enter in the composer should do.
 *
 * [InsertNewline] means "do not consume the key" — the field's own handling then puts a line break
 * in, which is what it did before this existed. There is deliberately no third "do nothing" case:
 * a consumed key that produces no visible effect is indistinguishable from a broken keyboard, and
 * a composer that swallows Enter is exactly the complaint #516 was filed about.
 */
enum class EnterKeyAction { Send, InsertNewline }

/**
 * The whole of #516's decision, kept out of the composable so it can be tested.
 *
 * The keypress itself cannot be tested on this host — there is no emulator, and the composer is a
 * Compose UI node — but every branch below can, and the branches are where the bug was: #514 wired
 * `ImeAction.Send` to a `KeyboardActions` handler and Enter still inserted a newline, because on a
 * multi-line field the IME draws a return key and the declared action never fires.
 *
 * @param enterToSend the user's preference (`ComposerSettingsController.enterToSend`).
 * @param shiftPressed whether Shift was held with the Enter that arrived.
 * @param hasSendableText whether the composer holds anything worth sending — blank is not.
 */
fun enterKeyAction(
    enterToSend: Boolean,
    shiftPressed: Boolean,
    hasSendableText: Boolean
): EnterKeyAction = when {
    // The setting off is the plain multi-line field: Enter is a line break, and the send button is
    // the only way to send. This is the half of the pair that people with a hardware keyboard and a
    // habit of typing paragraphs ask for.
    !enterToSend -> EnterKeyAction.InsertNewline

    // Shift+Enter is the escape hatch on a hardware keyboard, and the reason the field must stay
    // multi-line whichever way the setting is set. On a soft keyboard there is no Shift to hold, so
    // the setting itself is the escape hatch — which is also what WhatsApp does.
    shiftPressed -> EnterKeyAction.InsertNewline

    // Nothing to send. Falling through to a newline rather than consuming the key: consuming it
    // would make Enter feel dead in an empty composer, and a newline in blank text is still blank,
    // so this cannot produce a message of whitespace either.
    !hasSendableText -> EnterKeyAction.InsertNewline

    else -> EnterKeyAction.Send
}
