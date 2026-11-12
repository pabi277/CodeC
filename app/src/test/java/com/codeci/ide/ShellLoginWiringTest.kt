package com.codeci.ide

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 104.1 — source pins. The launch decision is pure and tested in
 * ShellLaunchPolicyTest; these pins prove the terminal really uses it, and
 * that the app-written ~/.profile sources ~/.bashrc behind a guard.
 */
class ShellLoginWiringTest {

    private fun source(path: String): String = RepoFiles.mainSource(path).readText()

    private val session = source("app/src/main/java/com/codeci/ide/ui/terminal/PtySession.kt")
    private val env = source("app/src/main/java/com/codeci/ide/ui/terminal/ShellEnvironment.kt")

    @Test
    fun `startShell asks the launch policy instead of building its own chain`() {
        assertTrue(session.contains("ShellLaunchPolicy.plan("))
        assertTrue(session.contains("ShellEnvironment.isElf(prepared.shell)"))
        assertTrue(session.contains("args = plan.argv.toTypedArray()"))
    }

    @Test
    fun `the home profile is written by the pure script and sources bashrc behind a guard`() {
        assertTrue(env.contains("homeProfileScript(File(etc, \"profile\").absolutePath)"))
        assertTrue(env.contains("fun homeProfileScript("))
        assertTrue(env.contains("if [ -f \\\"\\\$HOME/.bashrc\\\" ]; then . \\\"\\\$HOME/.bashrc\\\"; fi"))
    }
}
