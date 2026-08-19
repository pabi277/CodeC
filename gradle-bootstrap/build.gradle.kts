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
          --stacktrace \
          --project-cache-dir "${rootProject.file(".gradle/nested").absolutePath}" \
          2>&1 | tee "${'$'}log_file"; then
        exit 0
      fi

      python3 - "${'$'}log_file" <<'PY'
import pathlib
import sys

text = pathlib.Path(sys.argv[1]).read_text(errors="replace")
tail = "\n".join(text.splitlines()[-180:])
escaped = tail.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
print(f"::error title=Gradle verification failed::{escaped}")
PY
      exit 1
    """.trimIndent()
  )
}
