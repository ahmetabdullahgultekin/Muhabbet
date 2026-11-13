package com.muhabbet.app.ui.privacy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** #780: the export's stages, and what each event may and may not do from each of them. */
class DataExportStateTest {

    private fun DataExportState.after(vararg events: DataExportEvent): DataExportState =
        events.fold(this) { state, event -> reduceDataExport(state, event) }

    @Test
    fun should_walkFetchSaveDone_when_everythingSucceeds() {
        val states = listOf(
            DataExportEvent.Started,
            DataExportEvent.PageStarted(2),
            DataExportEvent.PageStarted(3),
            DataExportEvent.Assembled,
            DataExportEvent.Saved,
        ).runningFold(DataExportState.Idle as DataExportState) { s, e -> reduceDataExport(s, e) }

        assertEquals(
            listOf(
                DataExportState.Idle,
                DataExportState.Fetching(1),
                DataExportState.Fetching(2),
                DataExportState.Fetching(3),
                DataExportState.Saving,
                DataExportState.Done,
            ),
            states
        )
    }

    @Test
    fun should_ignoreASecondStart_when_anExportIsUnderWay() {
        assertEquals(DataExportState.Fetching(4), DataExportState.Fetching(4).after(DataExportEvent.Started))
        assertEquals(DataExportState.Saving, DataExportState.Saving.after(DataExportEvent.Started))
    }

    @Test
    fun should_returnToIdleWithoutAnError_when_theUserCancelsThePicker() {
        assertEquals(DataExportState.Idle, DataExportState.Saving.after(DataExportEvent.SaveCancelled))
    }

    @Test
    fun should_fail_when_fetchingOrSavingFails() {
        assertEquals(DataExportState.Failed, DataExportState.Fetching(2).after(DataExportEvent.Failed))
        assertEquals(DataExportState.Failed, DataExportState.Saving.after(DataExportEvent.Failed))
        // A stale failure after the outcome was shown does not resurrect it.
        assertEquals(DataExportState.Idle, DataExportState.Idle.after(DataExportEvent.Failed))
    }

    @Test
    fun should_offerTheButtonAgain_when_theOutcomeIsAcknowledgedOrAnotherExportStarts() {
        assertEquals(DataExportState.Idle, DataExportState.Done.after(DataExportEvent.Acknowledged))
        assertEquals(DataExportState.Idle, DataExportState.Failed.after(DataExportEvent.Acknowledged))
        assertEquals(DataExportState.Fetching(1), DataExportState.Failed.after(DataExportEvent.Started))
    }

    @Test
    fun should_beBusyOnlyWhileFetchingOrSaving() {
        assertTrue(DataExportState.Fetching(1).isBusy)
        assertTrue(DataExportState.Saving.isBusy)
        assertFalse(DataExportState.Idle.isBusy)
        assertFalse(DataExportState.Done.isBusy)
        assertFalse(DataExportState.Failed.isBusy)
    }

    @Test
    fun should_ignoreOutOfOrderEvents() {
        assertEquals(DataExportState.Idle, DataExportState.Idle.after(DataExportEvent.Saved))
        assertEquals(DataExportState.Idle, DataExportState.Idle.after(DataExportEvent.Assembled))
        assertEquals(DataExportState.Idle, DataExportState.Idle.after(DataExportEvent.PageStarted(9)))
    }

    @Test
    fun should_nameTheFileByDate() {
        assertEquals("muhabbet-data-export-2026-11-13.json", dataExportFileName("2026-11-13"))
    }
}
