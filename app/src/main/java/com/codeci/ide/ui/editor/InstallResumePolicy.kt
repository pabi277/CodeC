package com.codeci.ide.ui.editor

/** Identity of the editor file that requested an install-and-run operation. */
data class InstallRunContext(val projectName: String?, val fileName: String)

/** Navigation is unrestricted; install completion must not run a different file. */
object InstallResumePolicy {
    fun shouldResume(requested: InstallRunContext, current: InstallRunContext): Boolean =
        requested.fileName.isNotBlank() && requested == current
}
