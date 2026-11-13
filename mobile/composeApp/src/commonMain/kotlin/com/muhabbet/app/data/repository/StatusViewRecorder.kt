package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tells the server a status was seen, and remembers it locally (#787).
 *
 * A singleton with its own scope rather than a call from the viewer's composition scope, because
 * the viewer is exactly the screen people leave quickly: tapping through a story and backing out
 * cancels the composition, and a view report launched there would be cancelled with it — the story
 * would stay "unseen" for having been watched too fast.
 *
 * [viewed] is the local half. The Updates tab reloads when the viewer closes, but that reload can
 * overtake the POST; merging this set in means the ring changes the moment the user returns, and
 * the server's own `viewed` flag takes over on the next load.
 */
class StatusViewRecorder(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _viewed = MutableStateFlow<Set<String>>(emptySet())
    val viewed: StateFlow<Set<String>> = _viewed.asStateFlow()

    /**
     * Fire-and-forget, at most once per status per process. Returns the report's job (null when it
     * was already reported) for a caller that needs to wait on it — the screen does not.
     */
    fun markViewed(statusId: String): Job? {
        if (statusId in _viewed.value) return null
        _viewed.update { it + statusId }
        return scope.launch {
            runCatchingCancellable { apiClient.post<Unit>("/api/v1/statuses/$statusId/views", Unit) }
                .onFailure { e ->
                    // Forget it so the next viewing tries again; a lost view report only costs a
                    // ring that stays "unseen" until then.
                    Log.w(TAG, "Status view not recorded: ${e.message}")
                    _viewed.update { it - statusId }
                }
        }
    }

    private companion object {
        const val TAG = "StatusViewRecorder"
    }
}
