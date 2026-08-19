// Compatibility bridge for the legacy GitHub workflow. The real build runs
// under the checked-in, AGP-compatible Gradle wrapper.
tasks.register<Exec>("assembleDebug") {
  workingDir = rootDir
  commandLine(
    rootProject.file("gradlew").absolutePath,
    ":app:assembleDebug",
    ":app:testDebugUnitTest",
    ":app:lintDebug",
    "--no-daemon",
    "--stacktrace",
    "--project-cache-dir",
    rootProject.file(".gradle/nested").absolutePath
  )
}
