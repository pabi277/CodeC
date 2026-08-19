package com.codeci.ide.ui.modules

data class Module(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val size: Long,
    val downloadUrl: String,
    val checksum: String,
    val installPath: String,
    val executable: String,
    val dependencies: List<String> = emptyList(),
    val required: Boolean = false
)

sealed class InstallStatus {
    data object NotInstalled : InstallStatus()
    data class Downloading(val progress: Int) : InstallStatus()
    data object Extracting : InstallStatus()
    data object Installed : InstallStatus()
    data class Failed(val error: String) : InstallStatus()
}

data class ModuleState(
    val module: Module,
    val status: InstallStatus,
    val updateAvailable: Boolean = false
)
