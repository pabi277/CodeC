package com.codeci.ide

import com.codeci.ide.ui.editor.InstallResumePolicy
import com.codeci.ide.ui.editor.InstallRunContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallResumePolicyTest {
    private val requested = InstallRunContext("project-a", "src/main.py")

    @Test fun `same file keeps the explicit install-and-run intent`() {
        assertTrue(InstallResumePolicy.shouldResume(requested, requested.copy()))
    }

    @Test fun `changing tabs during installation must not run the new file`() {
        assertFalse(InstallResumePolicy.shouldResume(requested, requested.copy(fileName = "other.py")))
    }

    @Test fun `same relative name in a different project is not the requested file`() {
        assertFalse(InstallResumePolicy.shouldResume(requested, requested.copy(projectName = "project-b")))
    }

    @Test fun `scratch file identity is still checked`() {
        val scratch = InstallRunContext(null, "main.py")
        assertTrue(InstallResumePolicy.shouldResume(scratch, scratch))
        assertFalse(InstallResumePolicy.shouldResume(scratch, scratch.copy(fileName = "other.py")))
        assertFalse(InstallResumePolicy.shouldResume(scratch, scratch.copy(projectName = "project-a")))
    }

    @Test fun `closing the file must not cause a phantom run`() {
        assertFalse(InstallResumePolicy.shouldResume(requested, requested.copy(fileName = "")))
        val empty = InstallRunContext(null, "")
        assertFalse(InstallResumePolicy.shouldResume(empty, empty))
    }

    @Test fun `the ViewModel captures intent before async install and checks it before either run path`() {
        val vm = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt"
        ).readText().substringAfter("fun confirmInstall(context: Context)").substringBefore("override fun onCleared()")
        assertTrue(vm.indexOf("val installContext = InstallRunContext(") < vm.indexOf("runJob = viewModelScope.launch"))
        assertTrue(vm.contains("val resumeTarget = pendingRunTarget"))
        assertTrue(vm.contains("val resumeServerProject = pendingServerProject"))
        assertTrue(vm.contains("InstallResumePolicy.shouldResume("))
        val guard = vm.indexOf("if (!resume) return@launch")
        assertTrue(guard >= 0)
        assertTrue(guard < vm.indexOf("startServerRun(ctx, it)"))
        assertTrue(guard < vm.indexOf("runFile(ctx, resumeTarget)"))
        assertFalse(vm.contains("runFile(ctx, pendingRunTarget)"))
        assertTrue(vm.contains("R.string.output_install_ready"))
        assertTrue(vm.contains("phase = OutputPhase.DONE"))
    }
}
