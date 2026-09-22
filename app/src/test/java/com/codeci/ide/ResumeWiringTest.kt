package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 52.1 — source pins for the visible return door and its no-nag rules. */
class ResumeWiringTest {
    private val main = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/MainActivity.kt").readText()
    private val hub = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt"
    ).readText()
    private val launch = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/projects/EditorLaunchState.kt"
    ).readText()

    @Test fun `hub owns the card and its two actions`() {
        assertTrue(hub.contains("ResumeOfferCard("))
        assertTrue(hub.contains("stringResource(R.string.resume_continue)"))
        assertTrue(hub.contains("stringResource(R.string.resume_decline)"))
        assertTrue(hub.contains("onContinue = onResumeContinue"))
        assertTrue(hub.contains("onDecline = onResumeDecline"))
    }

    @Test fun `decline is activity session state not persisted state`() {
        assertTrue(main.contains("var resumeHandled by androidx.compose.runtime.saveable.rememberSaveable"))
        assertTrue(main.contains("onResumeDecline = { resumeHandled = true }"))
        assertFalse("the decline callback must not edit preferences", main.contains("resumeHandled = true\n                        scope.launch"))
    }

    @Test fun `launch state remains the single source of resumable file`() {
        assertTrue(main.contains("EditorLaunchState.load(activity)"))
        assertTrue(main.contains("stillExists = launchState != null"))
        assertTrue(launch.contains("ProjectPathUtils.resolveInside"))
        assertTrue(launch.contains("if (target.isFile) State"))
    }

    @Test fun `setup gate is resolved before resume offer`() {
        assertTrue(main.indexOf("val setupLaunchDivert") < main.indexOf("val resumeFacts"))
        assertTrue(main.contains("setupNeedsWatching = setupLaunchDivert"))
        assertTrue(main.contains("welcomePending = firstLaunchComplete == false"))
    }

    @Test fun `the timestamp is beside the existing shared preference`() {
        assertTrue(launch.contains("KEY_LAST_OPENED_AT"))
        assertTrue(launch.contains("putLong(KEY_LAST_OPENED_AT"))
        assertTrue(launch.contains("missing on older installs"))
    }
}
