package com.codeci.ide

import com.codeci.ide.ui.editor.NoticeKind
import com.codeci.ide.ui.editor.NoticePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 57.3 — which short message is owed, and when.
 *
 * Two laws, both worth more as tests than as comments: a file that cannot be
 * opened is **never** silent (it used to be exactly that — three `?: return`
 * paths with no message), and a refresh speaks **only** when the user asked for
 * it (the no-nag law the save snackbar already obeys).
 */
class NoticePolicyTest {

    @Test
    fun `an automatic refresh stays silent`() {
        assertNull(NoticePolicy.refreshNotice(userAsked = false))
    }

    @Test
    fun `the user's own refresh confirms itself`() {
        assertEquals(NoticeKind.FILES_REFRESHED, NoticePolicy.refreshNotice(userAsked = true))
    }

    @Test
    fun `a file that cannot open is never a dead tap`() {
        assertEquals(
            NoticeKind.FILE_OPEN_FAILED,
            NoticePolicy.openFailureNotice("src/main.c"),
        )
        assertEquals(
            NoticeKind.FILE_OPEN_FAILED,
            NoticePolicy.openFailureNotice("index.html"),
        )
    }

    @Test
    fun `nothing is said about nothing`() {
        assertNull(NoticePolicy.openFailureNotice(null))
        assertNull(NoticePolicy.openFailureNotice(""))
        assertNull(NoticePolicy.openFailureNotice("   "))
    }

    @Test
    fun `a run that needs a download says so only when the tools cannot take it`() {
        // Phase 58.2 — the owner's row: *"Userland installs silently; one
        // warning when a run needs a download before userland is ready."* The
        // verdict is `SetupGatePolicy.can(INSTALL_PACKAGE, …)`'s, so the warning
        // and the refusal it stands in for cannot drift apart.
        assertEquals(
            NoticeKind.USERLAND_NOT_READY,
            NoticePolicy.userlandWarning(packageInstallAllowed = false),
        )
        assertNull(
            "a ready setup gets the normal install sheet, not a warning",
            NoticePolicy.userlandWarning(packageInstallAllowed = true),
        )
    }

    @Test
    fun `the pill takes itself away, and not immediately`() {
        assertTrue(
            "a pill that leaves instantly is a pill nobody reads",
            NoticePolicy.AUTO_DISMISS_MS >= 1_500L,
        )
        assertTrue(
            "a pill that outstays its welcome becomes chrome",
            NoticePolicy.AUTO_DISMISS_MS <= 4_000L,
        )
    }
}
