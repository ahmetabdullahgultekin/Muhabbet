package com.muhabbet.app.ui.privacy

/**
 * The privacy dashboard's data export, as one sealed state with a pure reducer (#780, ADR 0012).
 *
 * It replaces an `isExporting` flag around a call that could not succeed. The export now has three
 * real phases — walk every page, hand the document to the system "save as", and learn whether the
 * user kept it — and each has something different to show and something different that can go
 * wrong. A flag cannot say "fetched page 4" or "the user backed out of the picker", and the second
 * of those is not a failure.
 */
sealed interface DataExportState {
    data object Idle : DataExportState

    /** Fetching page [page] of the export. */
    data class Fetching(val page: Int) : DataExportState

    /** Every page is in; the system "save as" is open. */
    data object Saving : DataExportState

    data object Done : DataExportState

    data object Failed : DataExportState
}

sealed interface DataExportEvent {
    data object Started : DataExportEvent
    data class PageStarted(val page: Int) : DataExportEvent
    data object Assembled : DataExportEvent
    data object Saved : DataExportEvent
    data object SaveCancelled : DataExportEvent
    data object Failed : DataExportEvent

    /** The user has seen the outcome; the button is offered again. */
    data object Acknowledged : DataExportEvent
}

/** True while an export is under way, so a second tap cannot start a second one. */
val DataExportState.isBusy: Boolean
    get() = this is DataExportState.Fetching || this == DataExportState.Saving

/**
 * Pure: an event that does not apply leaves the state alone. In particular [DataExportEvent.Started]
 * during an export is ignored rather than restarting it — two taps used to mean two full exports.
 */
fun reduceDataExport(state: DataExportState, event: DataExportEvent): DataExportState = when (event) {
    DataExportEvent.Started -> if (state.isBusy) state else DataExportState.Fetching(page = 1)

    is DataExportEvent.PageStarted ->
        if (state is DataExportState.Fetching) DataExportState.Fetching(event.page) else state

    DataExportEvent.Assembled ->
        if (state is DataExportState.Fetching) DataExportState.Saving else state

    DataExportEvent.Saved -> if (state == DataExportState.Saving) DataExportState.Done else state

    // Backing out of the picker is a choice, not an error: no red message, the button comes back.
    DataExportEvent.SaveCancelled -> if (state == DataExportState.Saving) DataExportState.Idle else state

    DataExportEvent.Failed -> if (state.isBusy) DataExportState.Failed else state

    DataExportEvent.Acknowledged -> when (state) {
        DataExportState.Done, DataExportState.Failed -> DataExportState.Idle
        else -> state
    }
}

/** The file name offered in the "save as" dialog; dated so repeated exports do not collide. */
fun dataExportFileName(isoDate: String): String = "muhabbet-data-export-$isoDate.json"
