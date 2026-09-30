package com.phlox.tvwebbrowser.model

import android.os.Bundle
import android.util.AtomicFile
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.utils.Utils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TVBro::class)
class SessionStateFileTest {
    @Test fun identicalSessionsHaveIndependentFiles() {
        val first = WebTabState().apply { savedState = Bundle().apply { putString("page", "same") } }
        val second = WebTabState().apply { savedState = Bundle().apply { putString("page", "same") } }
        first.saveWebViewStateToFile(false)
        second.saveWebViewStateToFile(false)
        assertNotNull(first.wvStateFileName)
        assertNotEquals(first.wvStateFileName, second.wvStateFileName)
        first.removeFiles()
        assertTrue(stateFile(second).exists())
        second.removeFiles()
    }

    @Test fun savingAgainUpdatesStateInSameFile() {
        val tab = WebTabState().apply { savedState = Bundle().apply { putString("page", "first") } }
        tab.saveWebViewStateToFile(false)
        val filename = tab.wvStateFileName
        tab.savedState = Bundle().apply { putString("page", "second") }
        tab.saveWebViewStateToFile(false)
        assertEquals(filename, tab.wvStateFileName)
        assertEquals("second", Utils.bytesToBundle(AtomicFile(stateFile(tab)).readFully())!!.getString("page"))
        tab.removeFiles()
    }

    private fun stateFile(tab: WebTabState) = File(AppContext.get().filesDir,
        "${WebTabState.TAB_WVSTATES_DIR}/${tab.wvStateFileName}")
}
