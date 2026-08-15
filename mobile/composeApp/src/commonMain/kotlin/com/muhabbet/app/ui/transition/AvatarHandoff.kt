package com.muhabbet.app.ui.transition

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The one shared element in the app: a conversation's avatar, flying from the list row into the
 * chat's title bar and back.
 *
 * ## Why this is a CompositionLocal
 *
 * The two participating avatars sit four levels apart — `MainContent` → `HomeShellScreen` →
 * `ConversationListScreen` → `ConversationListBody` → `ConversationRow` on one side, and
 * `MainContent` → `ChatScreen` on the other. Threading a `SharedTransitionScope` down as a
 * parameter would put a transition argument on five composables that otherwise know nothing about
 * transitions. This mirrors how `LocalSemanticColors` and `LocalTextStyles` already work in this
 * codebase.
 *
 * Deliberately nullable with a `null` default: any screen composed outside [ProvideAvatarHandoff]
 * gets an inert [handoffAvatar] rather than a crash, so the modifier can be applied unconditionally
 * at the call site.
 *
 * ## Why `sharedElementWithCallerManagedVisibility` and not `sharedElement`
 *
 * `sharedElement` requires an `AnimatedVisibilityScope`. Decompose's `Children` does not use
 * `AnimatedVisibility` — it hands a plain `Modifier` to a `StackAnimator` — so no such scope ever
 * exists here. The caller-managed variant is the only form that composes with Decompose, and the
 * price is that *we* decide which of the two copies is the real one. See [handoffAvatar].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Immutable
class AvatarHandoff(
    val scope: SharedTransitionScope,
    /**
     * The conversation whose chat screen is currently on top of the stack, or null when it is not.
     *
     * This is the single source of truth for which side owns the avatar. It flips at the moment the
     * stack changes — which is the moment the transition begins — so no extra bookkeeping is needed
     * to know which direction we are travelling in.
     */
    val activeConversationId: String?
)

val LocalAvatarHandoff = staticCompositionLocalOf<AvatarHandoff?> { null }

/**
 * Marks this avatar as one half of the list ↔ chat handoff.
 *
 * Exactly one of the two copies is visible at any instant, and the rule is a single comparison:
 * the chat's copy is real while its conversation is on top, the list's copy is real the rest of the
 * time. Whichever one becomes visible animates from where the other one was.
 *
 * @param isChatSide true for the copy in the chat title bar, false for the copy in the list row.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.handoffAvatar(conversationId: String, isChatSide: Boolean): Modifier {
    val handoff = LocalAvatarHandoff.current ?: return this
    val isChatOnTop = handoff.activeConversationId == conversationId
    return with(handoff.scope) {
        this@handoffAvatar.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key = "avatar:$conversationId"),
            visible = isChatSide == isChatOnTop
        )
    }
}
