package com.muhabbet.app.navigation

/**
 * A stack entry's identity, separated from its launch arguments (#825).
 *
 * Configs used to serve as both, through data-class equality over every field. Two entries for the
 * same conversation therefore counted as different screens whenever any argument differed — a
 * scroll target from Starred, a display name resolved differently by the notification path — and the
 * second was pushed on top of the first. The user saw the chat twice, and back revealed it again.
 *
 * Identity is now the entry's type plus [identity]: the conversation for a chat, the user for a
 * profile, nothing at all for a singleton screen such as Settings. Everything else a config carries
 * is an argument.
 */
interface NavDestination {
    /** What distinguishes two entries of the same type; null for a screen there is only ever one of. */
    val identity: String? get() = null
}

fun NavDestination.isSameDestinationAs(other: NavDestination): Boolean =
    this::class == other::class && identity == other.identity

/**
 * The one way an entry is opened: if the destination is already on the stack, return to it — pop
 * everything above it — and let [merge] decide what its arguments become; otherwise push it.
 *
 * A double tap is therefore harmless by construction (the second call finds the first entry on top
 * and changes nothing), and no screen can be on the stack twice.
 *
 * @param merge the arguments for an entry that was already there. Defaults to keeping the entry as
 *   it is, so that re-opening a screen does not recreate it and throw its state away.
 */
fun <C : NavDestination> List<C>.navigatedTo(
    target: C,
    merge: (existing: C, newer: C) -> C = { existing, _ -> existing }
): List<C> {
    val index = indexOfFirst { it.isSameDestinationAs(target) }
    return if (index < 0) this + target else take(index) + merge(this[index], target)
}

/**
 * Swaps the top entry for [target] in one operation — the "create, then show what was created" step,
 * where back should not return to the form. Same identity rule as [navigatedTo] for the rest.
 */
fun <C : NavDestination> List<C>.replacedTopWith(
    target: C,
    merge: (existing: C, newer: C) -> C = { existing, _ -> existing }
): List<C> = dropLast(1).navigatedTo(target, merge)
