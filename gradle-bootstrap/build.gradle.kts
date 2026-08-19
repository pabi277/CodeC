// Compatibility bridge for the legacy GitHub workflow. The real build runs
// under the checked-in, AGP-compatible Gradle wrapper.
tasks.register<Exec>("assembleDebug") {
  workingDir = rootDir
  commandLine(
    "bash",
    "-c",
    """
      set -o pipefail
      log_file="${rootProject.file(".gradle/nested-build.log").absolutePath}"
      mkdir -p "${rootProject.file(".gradle").absolutePath}"

      if "${rootProject.file("gradlew").absolutePath}" \
          :app:assembleDebug \
          :app:testDebugUnitTest \
          :app:lintDebug \
          --no-daemon \
          --project-cache-dir "${rootProject.file(".gradle/nested").absolutePath}" \
          2>&1 | tee "${'$'}log_file"; then
        exit 0
      fi

      python3 - "${'$'}log_file" <<'PY'
import pathlib
import sys

text = pathlib.Path(sys.argv[1]).read_text(errors="replace")
lines = text.splitlines()
needles = (
    "e: file:",
    "error:",
    "FAILURE:",
    "* What went wrong:",
    "Execution failed for task",
    "There were failing tests",
    "Lint found",
)
diagnostics = [line for line in lines if any(needle in line for needle in needles)]
if not diagnostics:
    diagnostics = lines[-80:]

for index, diagnostic in enumerate(diagnostics[:40], 1):
    escaped = diagnostic.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::error title=Gradle failure {index}::{escaped}")
PY
      exit 1
    """.trimIndent()
  )
}
